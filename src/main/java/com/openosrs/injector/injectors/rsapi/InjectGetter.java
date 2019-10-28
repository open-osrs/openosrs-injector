package com.openosrs.injector.injectors.rsapi;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.rsapi.RSApiMethod;
import java.util.List;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Field;
import net.runelite.asm.Method;
import net.runelite.asm.attributes.Code;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.GetField;
import net.runelite.asm.attributes.code.instructions.GetStatic;
import net.runelite.asm.attributes.code.instructions.IMul;
import net.runelite.asm.attributes.code.instructions.LDC;
import net.runelite.asm.attributes.code.instructions.LMul;
import net.runelite.asm.signature.Signature;

public class InjectGetter
{
	public static void inject(ClassFile targetClass, RSApiMethod apiMethod, Field field, Number getter) throws Injexception
	{
		if (targetClass.findMethod(apiMethod.getName(), apiMethod.getSignature()) != null)
		{
			throw new Injexception("Duplicate getter method " + apiMethod.getMethod().toString());
		}

		final String name = apiMethod.getName();
		final Signature sig = apiMethod.getSignature();

		final Method method = new Method(targetClass, name, sig);
		method.setPublic();

		final Code code = new Code(method);
		method.setCode(code);

		final Instructions instructions = code.getInstructions();
		final List<Instruction> ins = instructions.getInstructions();

		if (field.isStatic())
		{
			ins.add(new GetStatic(instructions, field.getPoolField()));
		}
		else
		{
			ins.add(new ALoad(instructions, 0));
			ins.add(new GetField(instructions, field.getPoolField()));
		}

		if (getter instanceof Integer)
		{
			ins.add(new LDC(instructions, getter));
			ins.add(new IMul(instructions));
		}
		else if (getter instanceof Long)
		{
			ins.add(new LDC(instructions, getter));
			ins.add(new LMul(instructions));
		}

		ins.add(InjectUtil.createReturnForType(instructions, field.getType()));

		targetClass.addMethod(method);
	}
}
