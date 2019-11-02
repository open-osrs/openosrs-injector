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
			injectMethods(entry.getKey(), entry.getValue());

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
					continue;

				if (!mixinMethod.getDescriptor().isVoid())
					throw new Injexception("Method hook " + mixinMethod.getPoolMethod() + " doesn't have void return type");

				final String hookName = methodHook.getElement().getString();
				final boolean end = methodHook.getElements().size() == 2 && methodHook.getElements().get(1).getValue().equals(true);

				final ClassFile deobTarget = inject.toDeob(targetClass.getName());
				final Signature deobSig = InjectUtil.apiToDeob(inject, mixinMethod.getDescriptor());
				final boolean notStatic = !mixinMethod.isStatic();
				final Method targetMethod = InjectUtil.findMethod(inject, hookName, deobTarget.getName(), sig -> InjectUtil.argsMatch(sig, deobSig), notStatic, false);

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
		final ListIterator<Instruction> it;

		if (end)
		{
			it = ins.listIterator(ins.size());
			while (it.hasPrevious())
				if (it.previous() instanceof ReturnInstruction)
					insertVoke(method, hookMethod, it);

			return;
		}

		it = ins.listIterator();
		if (method.getName().equals("<init>"))
		{
			while (it.hasNext())
				if (it.next() instanceof InvokeSpecial)
					break;

			assert it.hasNext() : "Constructor without invokespecial";
		}

		insertVoke(method, hookMethod, it);
	}

	private void insertVoke(final Method method, final net.runelite.asm.pool.Method hookMethod, ListIterator<Instruction> iterator)
	{
		final Instructions instructions = method.getCode().getInstructions();
		int varIdx = 0;

		if (!method.isStatic())
			iterator.add(new ALoad(instructions, varIdx++));

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
