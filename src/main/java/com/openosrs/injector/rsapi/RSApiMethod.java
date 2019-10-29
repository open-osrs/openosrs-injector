package com.openosrs.injector.rsapi;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.runelite.asm.Annotated;
import net.runelite.asm.Named;
import net.runelite.asm.attributes.Annotations;
import net.runelite.asm.pool.Class;
import net.runelite.asm.pool.Method;
import net.runelite.asm.signature.Signature;
import org.objectweb.asm.Opcodes;

@Data
@RequiredArgsConstructor
public class RSApiMethod implements Annotated, Named
{
	private final Method method;
	private final int accessFlags;
	private final Annotations annotations = new Annotations();
	private boolean injected;

	public Class getClazz()
	{
		return method.getClazz();
	}

	public String getName()
	{
		return method.getName();
	}

	public Signature getSignature()
	{
		return method.getType();
	}

	public boolean isSynthetic()
	{
		return (accessFlags & Opcodes.ACC_SYNTHETIC) != 0;
	}

	public boolean isDefault()
	{
		return (accessFlags & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == 1;
	}
}
