package com.openosrs.injector.rsapi;

import net.runelite.asm.pool.Class;
import net.runelite.asm.signature.Signature;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RSApiClassVisitor extends ClassVisitor
{
	private final RSApiClass apiClass;

	public RSApiClassVisitor(RSApiClass apiClass)
	{
		super(Opcodes.ASM5);
		this.apiClass = apiClass;
	}

	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
	{
		apiClass.setClazz(new Class(name));

		for (String s : interfaces)
		{
			apiClass.getInterfaces().add(new Class(s));
		}
	}

	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
	{
		final RSApiMethod method = apiClass.addMethod(name, new Signature(desc), access);
		return new RSApiMethodVisitor(method);
	}
}
