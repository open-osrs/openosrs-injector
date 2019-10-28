package com.openosrs.injector.injectors;

import com.google.common.collect.Lists;
import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import lombok.AllArgsConstructor;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Field;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.attributes.Code;
import net.runelite.asm.attributes.annotation.Annotation;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.InstructionType;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instruction.types.DupInstruction;
import net.runelite.asm.attributes.code.instruction.types.SetFieldInstruction;
import net.runelite.asm.attributes.code.instructions.ArrayStore;
import net.runelite.asm.attributes.code.instructions.CheckCast;
import net.runelite.asm.attributes.code.instructions.Dup;
import net.runelite.asm.attributes.code.instructions.IMul;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.attributes.code.instructions.InvokeVirtual;
import net.runelite.asm.attributes.code.instructions.LDC;
import net.runelite.asm.attributes.code.instructions.LMul;
import net.runelite.asm.attributes.code.instructions.PutField;
import net.runelite.asm.attributes.code.instructions.Swap;
import net.runelite.asm.execution.Execution;
import net.runelite.asm.execution.InstructionContext;
import net.runelite.asm.execution.StackContext;
import net.runelite.asm.signature.Signature;
import net.runelite.deob.DeobAnnotations;

public class InjectHook extends AbstractInjector
{
	private static final String HOOK_METHOD_SIGNATURE = "(I)V";
	private static final String CLINIT = "<clinit>";
	private static final Type FIELDHOOK = new Type("Lnet/runelite/api/mixins/FieldHook;");

	private final Map<Field, HookInfo> hooked = new HashMap<>();
	private final Map<Provider<ClassFile>, List<ClassFile>> mixinTargets;

	private int injectedHooks;

	InjectHook(final InjectData inject, final Map<Provider<ClassFile>, List<ClassFile>> mixinTargets)
	{
		super(inject);
		this.mixinTargets = mixinTargets;
	}

	@Override
	public void inject() throws Injexception
	{
		for (Map.Entry<Provider<ClassFile>, List<ClassFile>> entry : mixinTargets.entrySet())
		{
			injectMethods(entry.getKey(), entry.getValue());
		}

		injectHooks();

		log.info("Injected {} field hooks.", injectedHooks);
	}

	private void injectMethods(Provider<ClassFile> mixinProvider, List<ClassFile> targetClasses) throws Injexception
	{
		final ClassFile mixinClass = mixinProvider.get();

		for (ClassFile targetClass : targetClasses)
		{
			for (Method mixinMethod : mixinClass.getMethods())
			{
				final Annotation fieldHook = mixinMethod.getAnnotations().find(FIELDHOOK);
				if (fieldHook == null)
				{
					continue;
				}

				final String hookName = fieldHook.getElement().getString();
				final boolean before = fieldHook.getElements().size() == 2 && fieldHook.getElements().get(1).getValue().equals(true);

				final ClassFile deobTarget = inject.toDeob(targetClass.getName());
				final Field deobField;

				if (mixinMethod.isStatic())
				{
					deobField = InjectUtil.findStaticField(deobTarget.getGroup(), hookName);
				}
				else
				{
					deobField = InjectUtil.findFieldDeep(deobTarget, hookName);
				}

				assert mixinMethod.isStatic() == deobField.isStatic() : "Mixin method isn't static but deob has a static method named the same as the hook, and I was too lazy to do something about this bug";

				final Number getter = DeobAnnotations.getObfuscatedGetter(deobField);
				final Field obField = inject.toVanilla(deobField);

				final HookInfo info = new HookInfo(mixinClass.getName(), hookName, mixinMethod, before, getter);

				hooked.put(obField, info);
			}
		}
	}

	private void injectHooks()
	{
		Execution e = new Execution(inject.getVanilla());
		e.populateInitialMethods();

		Set<Instruction> done = new HashSet<>();
		Set<Instruction> doneIh = new HashSet<>();

		e.addExecutionVisitor((
		InstructionContext ic) ->
		{
			Instruction i = ic.getInstruction();
			Instructions ins = i.getInstructions();
			Code code = ins.getCode();
			Method method = code.getMethod();

			if (method.getName().equals(CLINIT))
			{
				return;
			}

			if (!(i instanceof SetFieldInstruction))
			{
				return;
			}

			if (!done.add(i))
			{
				return;
			}

			SetFieldInstruction sfi = (SetFieldInstruction) i;
			Field fieldBeingSet = sfi.getMyField();

			if (fieldBeingSet == null)
			{
				return;
			}

			HookInfo hookInfo = hooked.get(fieldBeingSet);
			if (hookInfo == null)
			{
				return;
			}

			String hookName = hookInfo.fieldName;
			assert hookName != null;

			log.trace("Found injection location for hook {} at instruction {}", hookName, sfi);
			++injectedHooks;

			StackContext value = ic.getPops().get(0);

			StackContext objectStackContext = null;
			if (sfi instanceof PutField)
			{
				objectStackContext = ic.getPops().get(1);
			}

			int idx = ins.getInstructions().indexOf(sfi);
			assert idx != -1;

			try
			{
				if (hookInfo.before)
				{
					injectCallbackBefore(ins, idx, hookInfo, null, objectStackContext, value);
				}
				else
				{
					// idx + 1 to insert after the set
					injectCallback(ins, idx + 1, hookInfo, null, objectStackContext);
				}
			}
			catch (Injexception ex)
			{
				throw new RuntimeException(ex);
			}
		});

		// these look like:
		// getfield
		// iload_0
		// iconst_0
		// iastore
		e.addExecutionVisitor((InstructionContext ic) ->
		{
			Instruction i = ic.getInstruction();
			Instructions ins = i.getInstructions();
			Code code = ins.getCode();
			Method method = code.getMethod();

			if (method.getName().equals(CLINIT))
			{
				return;
			}

			if (!(i instanceof ArrayStore))
			{
				return;
			}

			if (!doneIh.add(i))
			{
				return;
			}

			ArrayStore as = (ArrayStore) i;

			Field fieldBeingSet = as.getMyField(ic);
			if (fieldBeingSet == null)
			{
				return;
			}

			HookInfo hookInfo = hooked.get(fieldBeingSet);
			if (hookInfo == null)
			{
				return;
			}

			String hookName = hookInfo.fieldName;

			StackContext value = ic.getPops().get(0);
			StackContext index = ic.getPops().get(1);

			StackContext arrayReference = ic.getPops().get(2);
			InstructionContext arrayReferencePushed = arrayReference.getPushed();

			StackContext objectStackContext = null;
			if (arrayReferencePushed.getInstruction().getType() == InstructionType.GETFIELD)
			{
				objectStackContext = arrayReferencePushed.getPops().get(0);
			}

			// inject hook after 'i'
			log.debug("Found array injection location for hook {} at instruction {}", hookName, i);
			++injectedHooks;

			int idx = ins.getInstructions().indexOf(i);
			assert idx != -1;

			try
			{
				if (hookInfo.before)
				{
					injectCallbackBefore(ins, idx, hookInfo, index, objectStackContext, value);
				}
				else
				{
					injectCallback(ins, idx + 1, hookInfo, index, objectStackContext);
				}
			}
			catch (Injexception ex)
			{
				throw new RuntimeException(ex);
			}
		});

		e.run();
	}

	private void injectCallbackBefore(Instructions ins, int idx, HookInfo hookInfo, StackContext index, StackContext object, StackContext value) throws Injexception
	{
		Signature signature = hookInfo.method.getDescriptor();
		Type methodArgumentType = signature.getTypeOfArg(0);

		if (!hookInfo.method.isStatic())
		{
			if (object == null)
			{
				throw new Injexception("null object");
			}

			ins.getInstructions().add(idx++, new Dup(ins)); // dup value
			idx = recursivelyPush(ins, idx, object);
			ins.getInstructions().add(idx++, new Swap(ins));

			if (hookInfo.getter instanceof Integer)
			{
				ins.getInstructions().add(idx++, new LDC(ins, hookInfo.getter));
				ins.getInstructions().add(idx++, new IMul(ins));
			}
			else if (hookInfo.getter instanceof Long)
			{
				ins.getInstructions().add(idx++, new LDC(ins, hookInfo.getter));
				ins.getInstructions().add(idx++, new LMul(ins));
			}

			if (!value.type.equals(methodArgumentType))
			{
				CheckCast checkCast = new CheckCast(ins);
				checkCast.setType(methodArgumentType);
				ins.getInstructions().add(idx++, checkCast);
			}
			if (index != null)
			{
				idx = recursivelyPush(ins, idx, index);
			}

			InvokeVirtual invoke = new InvokeVirtual(ins,
				new net.runelite.asm.pool.Method(
					new net.runelite.asm.pool.Class(hookInfo.clazz),
					hookInfo.method.getName(),
					signature
				)
			);
			ins.getInstructions().add(idx++, invoke);
		}
		else
		{
			ins.getInstructions().add(idx++, new Dup(ins)); // dup value
			if (!value.type.equals(methodArgumentType))
			{
				CheckCast checkCast = new CheckCast(ins);
				checkCast.setType(methodArgumentType);
				ins.getInstructions().add(idx++, checkCast);
			}
			if (index != null)
			{
				idx = recursivelyPush(ins, idx, index);
			}

			InvokeStatic invoke = new InvokeStatic(ins,
				new net.runelite.asm.pool.Method(
					new net.runelite.asm.pool.Class(hookInfo.clazz),
					hookInfo.method.getName(),
					signature
				)
			);
			ins.getInstructions().add(idx++, invoke);
		}
	}

	private int recursivelyPush(Instructions ins, int idx, StackContext sctx)
	{
		InstructionContext ctx = sctx.getPushed();
		if (ctx.getInstruction() instanceof DupInstruction)
		{
			DupInstruction dupInstruction = (DupInstruction) ctx.getInstruction();
			sctx = dupInstruction.getOriginal(sctx);
			ctx = sctx.getPushed();
		}

		for (StackContext s : Lists.reverse(ctx.getPops()))
		{
			idx = recursivelyPush(ins, idx, s);
		}

		ins.getInstructions().add(idx++, ctx.getInstruction().clone());
		return idx;
	}

	private void injectCallback(Instructions ins, int idx, HookInfo hookInfo, StackContext index, StackContext objectPusher) throws Injexception
	{
		if (!hookInfo.method.isStatic())
		{
			if (objectPusher == null)
			{
				throw new Injexception("Null object pusher");
			}

			idx = recursivelyPush(ins, idx, objectPusher);
			if (index != null)
			{
				idx = recursivelyPush(ins, idx, index);
			}
			else
			{
				ins.getInstructions().add(idx++, new LDC(ins, -1));
			}

			InvokeVirtual invoke = new InvokeVirtual(ins,
				new net.runelite.asm.pool.Method(
					new net.runelite.asm.pool.Class(hookInfo.clazz),
					hookInfo.method.getName(),
					new Signature(HOOK_METHOD_SIGNATURE)
				)
			);
			ins.getInstructions().add(idx++, invoke);

		}
		else
		{
			if (index != null)
			{
				idx = recursivelyPush(ins, idx, index);
			}
			else
			{
				ins.getInstructions().add(idx++, new LDC(ins, -1));
			}

			InvokeStatic invoke = new InvokeStatic(ins,
				new net.runelite.asm.pool.Method(
					new net.runelite.asm.pool.Class(hookInfo.clazz),
					hookInfo.method.getName(),
					new Signature(HOOK_METHOD_SIGNATURE)
				)
			);
			ins.getInstructions().add(idx++, invoke);
		}
	}

	public String getName()
	{
		return super.getName();
	}

	@AllArgsConstructor
	static class HookInfo
	{
		String fieldName;
		String clazz;
		Method method;
		boolean before;
		Number getter;
	}
}
