package com.openosrs.injector.injectors.rsapi;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.rsapi.RSApiMethod;
import java.util.List;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.attributes.Code;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.BiPush;
import net.runelite.asm.attributes.code.instructions.CheckCast;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.attributes.code.instructions.InvokeVirtual;
import net.runelite.asm.attributes.code.instructions.LDC;
import net.runelite.asm.attributes.code.instructions.SiPush;
import net.runelite.asm.signature.Signature;

public class InjectInvoke
{
	public static void inject(ClassFile targetClass, RSApiMethod apiMethod, Method vanillaMethod, String garbage) throws Injexception
	{
		if (targetClass.findMethod(apiMethod.getName(), apiMethod.getSignature()) != null)
		{
			throw new Injexception("Duplicate invoker method " + apiMethod.getMethod().toString());
		}

		final Method method = new Method(targetClass, apiMethod.getName(), apiMethod.getSignature());
		method.setPublic();

		final Code code = new Code(method);
		method.setCode(code);

		final Instructions instructions = code.getInstructions();
		final List<Instruction> ins = instructions.getInstructions();

		int varIdx = 0;
		if (!vanillaMethod.isStatic())
		{
			ins.add(new ALoad(instructions, varIdx++));
		}

		final Signature apiSig = apiMethod.getSignature();
		final Signature vanSig = vanillaMethod.getDescriptor();
		for (int i = 0; i < apiSig.size(); i++)
		{
			final Type type = apiSig.getTypeOfArg(i);
			final Instruction loadInstruction = InjectUtil.createLoadForTypeIndex(instructions, type, varIdx);
			ins.add(loadInstruction);

			final Type obType = vanSig.getTypeOfArg(i);
			if (!type.equals(obType))
			{
				final CheckCast checkCast = new CheckCast(instructions);
				checkCast.setType(obType);
				ins.add(checkCast);
			}

			varIdx += type.getSize();
		}

		if (apiSig.size() != vanSig.size())
		{
			if (garbage == null)
			{
				garbage = "0";
			}

			switch (vanSig.getTypeOfArg(vanSig.size() - 1).toString())
			{
				case "Z":
				case "B":
				case "C":
					ins.add(new BiPush(instructions, Byte.parseByte(garbage)));
					break;
				case "S":
					ins.add(new SiPush(instructions, Short.parseShort(garbage)));
					break;
				case "I":
					ins.add(new LDC(instructions, Integer.parseInt(garbage)));
					break;
				case "D":
					ins.add(new LDC(instructions, Double.parseDouble(garbage)));
					break;
				case "F":
					ins.add(new LDC(instructions, Float.parseFloat(garbage)));
					break;
				case "J":
					ins.add(new LDC(instructions, Long.parseLong(garbage)));
					break;
				default:
					throw new RuntimeException("Unknown type");
			}
		}

		if (vanillaMethod.isStatic())
		{
			ins.add(new InvokeStatic(instructions, vanillaMethod.getPoolMethod()));
		}
		else
		{
			ins.add(new InvokeVirtual(instructions, vanillaMethod.getPoolMethod()));
		}

		ins.add(InjectUtil.createReturnForType(instructions, vanSig.getReturnValue()));

		targetClass.addMethod(method);
	}
}
