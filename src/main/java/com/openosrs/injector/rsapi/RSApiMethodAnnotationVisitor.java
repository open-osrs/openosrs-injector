/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this source tree.
 */
package com.openosrs.injector.rsapi;

import net.runelite.asm.attributes.annotation.Annotation;
import net.runelite.asm.attributes.annotation.Element;
import net.runelite.asm.attributes.annotation.SimpleElement;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

public class RSApiMethodAnnotationVisitor extends AnnotationVisitor
{
	private final Annotation annotation;

	RSApiMethodAnnotationVisitor(Annotation annotation)
	{
		super(Opcodes.ASM5);

		this.annotation = annotation;
	}

	@Override
	public void visit(String name, Object value)
	{
		Element element = new SimpleElement(name, value);

		annotation.addElement(element);
	}
}
