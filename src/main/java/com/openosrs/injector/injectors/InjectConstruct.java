package com.openosrs.injector.injectors;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.rsapi.RSApiMethod;
import static com.openosrs.injector.rsapi.RSApi.CONSTRUCT;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Type;
import net.runelite.asm.attributes.Code;
import net.runelite.asm.attributes.annotation.Annotation;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instructions.CheckCast;
import net.runelite.asm.attributes.code.instructions.Dup;
import net.runelite.asm.attributes.code.instructions.InvokeSpecial;
import net.runelite.asm.attributes.code.instructions.New;
import net.runelite.asm.attributes.code.instructions.Return;
import net.runelite.asm.pool.Class;
import net.runelite.asm.pool.Method;
import net.runelite.asm.signature.Signature;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

public class InjectConstruct extends AbstractInjector
{
	private int injected = 0;

	public InjectConstruct(InjectData inject)
	{
		super(inject);
	}

	@Override
	public void inject() throws Injexception
	{
		for (RSApiMethod apiMethod : inject.getRsApi().getConstructs())
		{
			Annotation construct = apiMethod.getAnnotations().find(CONSTRUCT);
			if (construct == null)
				continue;

			final Method method = apiMethod.getMethod();
			final Class clazz = method.getClazz();
			final ClassFile deobClass = inject.toDeob(clazz.getName());
			final ClassFile vanillaClass = inject.toVanilla(deobClass);

			injectConstruct(vanillaClass, method);
			apiMethod.setInjected(true);
			injected++;
		}

		log.info("Injected {} constructors", injected);
	}

	private void injectConstruct(ClassFile targetClass, Method apiMethod) throws Injexception
	{
		log.debug("Injecting constructor for {} into {}", apiMethod, targetClass.getPoolClass());

		final Type returnval = apiMethod.getType().getReturnValue();

		final ClassFile deobClass = inject.toDeob(returnval.getInternalName());
		final ClassFile classToConstruct = inject.toVanilla(deobClass);

		Signature constr = new Signature.Builder()
			.addArguments(apiMethod.getType().getArguments().stream()
				.map(t -> InjectUtil.apiToDeob(inject, t))
				.map(t -> InjectUtil.deobToVanilla(inject, t))
				.collect(Collectors.toList()))
			.setReturnType(Type.VOID)
			.build();

		final net.runelite.asm.Method constructor = classToConstruct.findMethod("<init>", constr);
		if (constructor == null)
			throw new Injexception("Unable to find constructor for " + classToConstruct.getName() + ".<init>" + constr);


		net.runelite.asm.Method setterMethod = new net.runelite.asm.Method(targetClass, apiMethod.getName(), apiMethod.getType());
		setterMethod.setAccessFlags(ACC_PUBLIC);
		targetClass.addMethod(setterMethod);

		final Code code = new Code(setterMethod);
		setterMethod.setCode(code);

		final Instructions instructions = code.getInstructions();
		final List<Instruction> ins = instructions.getInstructions();

		ins.add(new New(instructions, classToConstruct.getPoolClass()));
		ins.add(new Dup(instructions));

		int idx = 1;
		int parameter = 0;

		for (Type type : constructor.getDescriptor().getArguments())
		{
			Instruction load = InjectUtil.createLoadForTypeIndex(instructions, type, idx);
			idx += type.getSize();
			ins.add(load);

			Type paramType = apiMethod.getType().getTypeOfArg(parameter);
			if (!type.equals(paramType))
			{
				CheckCast checkCast = new CheckCast(instructions);
				checkCast.setType(type);
				ins.add(checkCast);
			}

			++parameter;
		}

		ins.add(new InvokeSpecial(instructions, constructor.getPoolMethod()));
		ins.add(new Return(instructions));
	}
}
