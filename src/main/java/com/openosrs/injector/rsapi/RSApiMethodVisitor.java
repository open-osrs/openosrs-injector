package com.openosrs.injector.rsapi;

import net.runelite.asm.Type;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RSApiMethodVisitor extends MethodVisitor
{
	private final RSApiMethod method;

	RSApiMethodVisitor(RSApiMethod method)
	{
		super(Opcodes.ASM5);
		this.method = method;
	}

	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible)
	{
		final Type type = new Type(descriptor);

		return new RSApiMethodAnnotationVisitor(method, type);
	}
}
