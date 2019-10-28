package com.openosrs.injector.injectors.rsapi;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.rsapi.RSApiMethod;
import java.util.List;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Field;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.attributes.Code;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.CheckCast;
import net.runelite.asm.attributes.code.instructions.IMul;
import net.runelite.asm.attributes.code.instructions.LDC;
import net.runelite.asm.attributes.code.instructions.PutField;
import net.runelite.asm.attributes.code.instructions.PutStatic;
import net.runelite.asm.attributes.code.instructions.VReturn;
import net.runelite.asm.signature.Signature;

public class InjectSetter
{
	public static void inject(ClassFile targetClass, RSApiMethod apiMethod, Field field, Number setter) throws Injexception
	{
		if (targetClass.findMethod(apiMethod.getName(), apiMethod.getSignature()) != null)
		{
			throw new Injexception("Duplicate setter method " + apiMethod.getMethod().toString());
		}

		final String name = apiMethod.getName();
		final Signature sig = apiMethod.getSignature();

		final Method method = new Method(targetClass, name, sig);
		method.setPublic();


		final Code code = new Code(method);
		method.setCode(code);

		final Instructions instructions = code.getInstructions();
		final List<Instruction> ins = instructions.getInstructions();

		// load this
		if (!field.isStatic())
		{
			ins.add(new ALoad(instructions, 0));
		}

		// load argument
		final Type argumentType = sig.getTypeOfArg(0);
		ins.add(InjectUtil.createLoadForTypeIndex(instructions, argumentType, 1));

		// cast argument to field type
		final Type fieldType = field.getType();
		if (!argumentType.equals(fieldType))
		{
			CheckCast checkCast = new CheckCast(instructions);
			checkCast.setType(fieldType);
			ins.add(checkCast);
		}

		if (setter instanceof Integer)
		{
			ins.add(new LDC(instructions, setter));
			ins.add(new IMul(instructions));
		}
		else if (setter instanceof Long)
		{
			ins.add(new LDC(instructions, setter));
			ins.add(new IMul(instructions));
		}

		if (field.isStatic())
		{
			ins.add(new PutStatic(instructions, field));
		}
		else
		{
			ins.add(new PutField(instructions, field));
		}

		ins.add(new VReturn(instructions));

		targetClass.addMethod(method);
	}
}
