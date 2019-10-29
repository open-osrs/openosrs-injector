package com.openosrs.injector.injectors;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import javax.inject.Provider;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.attributes.annotation.Annotation;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instruction.types.ReturnInstruction;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.InvokeSpecial;
import net.runelite.asm.signature.Signature;

public class InjectHookMethod extends AbstractInjector
{
	private static final Type METHODHOOK = new Type("Lnet/runelite/api/mixins/MethodHook;");
	private final Map<Provider<ClassFile>, List<ClassFile>> mixinTargets;

	private int injected = 0;

	InjectHookMethod(final InjectData inject, final Map<Provider<ClassFile>, List<ClassFile>> mixinTargets)
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

		log.info("Injected {} method hooks", injected);
	}

	private void injectMethods(Provider<ClassFile> mixinProvider, List<ClassFile> targetClasses) throws Injexception
	{
		final ClassFile mixinClass = mixinProvider.get();

		for (ClassFile targetClass : targetClasses)
		{
			for (Method mixinMethod : mixinClass.getMethods())
			{
				final Annotation methodHook = mixinMethod.getAnnotations().find(METHODHOOK);
				if (methodHook == null)
				{
					continue;
				}

				if (!mixinMethod.getDescriptor().isVoid())
					throw new Injexception("Method hook " + mixinMethod.getPoolMethod() + " doesn't have void return type");

				final String hookName = methodHook.getElement().getString();
				final boolean end = methodHook.getElements().size() == 2 && methodHook.getElements().get(1).getValue().equals(true);

				final ClassFile deobTarget = inject.toDeob(targetClass.getName());
				final Signature deobSig = InjectUtil.apiToDeob(inject, mixinMethod.getDescriptor());

				final Method targetMethod;
				if (mixinMethod.isStatic())
				{
					targetMethod = InjectUtil.findMethodWithArgs(inject, hookName, deobTarget.getName(), deobSig); // , deobSig);
				}
				else
				{
					targetMethod = InjectUtil.findMethodWithArgsDeep(inject, deobTarget, hookName, deobSig);
				}

				assert mixinMethod.isStatic() == targetMethod.isStatic() : "Mixin method isn't static but deob has a static method named the same as the hook, and I was too lazy to do something about this bug";

				final net.runelite.asm.pool.Method hookMethod = new net.runelite.asm.pool.Method(
					targetClass.getPoolClass(),
					mixinMethod.getName(),
					mixinMethod.getDescriptor()
				);

				inject(targetMethod, hookMethod, end);

				log.debug("Injected method hook {} in {}", hookMethod, targetMethod);
				++injected;
			}
		}
	}

	private void inject(final Method method, final net.runelite.asm.pool.Method hookMethod, boolean end)
	{
		final Instructions ins = method.getCode().getInstructions();

		if (end)
		{
			final ListIterator<Instruction> it = ins.listIterator(ins.size() - 1);
			while (it.hasPrevious())
			{
				if (it.previous() instanceof ReturnInstruction)
				{
					insertVoke(method, hookMethod, it);
				}
			}

			return;
		}

		final ListIterator<Instruction> it = ins.listIterator();

		if (method.getName().equals("<init>"))
		{
			while (it.hasNext())
			{
				if (it.next() instanceof InvokeSpecial)
				{
					break;
				}
			}

			assert it.hasNext() : "Constructor without invokespecial";
		}

		insertVoke(method, hookMethod, it);
	}

	private void insertVoke(final Method method, final net.runelite.asm.pool.Method hookMethod, ListIterator<Instruction> iterator)
	{
		final Instructions instructions = method.getCode().getInstructions();
		int varIdx = 0;

		if (!method.isStatic())
		{
			iterator.add(new ALoad(instructions, varIdx++));
		}

		for (Type type : hookMethod.getType().getArguments())
		{
			iterator.add(
				InjectUtil.createLoadForTypeIndex(
					instructions,
					type,
					varIdx
				)
			);

			varIdx += type.getSize();
		}

		iterator.add(
			InjectUtil.createInvokeFor(
				instructions,
				hookMethod,
				method.isStatic()
			)
		);
	}
}
