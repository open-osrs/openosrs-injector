package com.openosrs.injector.injectors.raw;

import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import static com.openosrs.injector.injection.InjectData.HOOKS;
import com.openosrs.injector.injectors.AbstractInjector;
import java.util.ListIterator;
import net.runelite.asm.ClassGroup;
import net.runelite.asm.Method;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.attributes.code.instructions.InvokeVirtual;
import net.runelite.asm.pool.Class;
import net.runelite.asm.signature.Signature;

public class RenderDraw extends AbstractInjector
{
	private static final net.runelite.asm.pool.Method RENDERDRAW = new net.runelite.asm.pool.Method(
		new Class(HOOKS),
		"renderDraw",
		new Signature("(Lnet/runelite/api/Entity;IIIIIIIIJ)V")
	);
	private static final int EXPECTED = 21;

	public RenderDraw(InjectData inject)
	{
		super(inject);
	}

	@Override
	public void inject() throws Injexception
	{
		int replaced = 0;

		/*
		 * This class replaces entity draw invocation instructions
		 * with the renderDraw method on drawcallbacks
		 */
		final ClassGroup deob = inject.getDeobfuscated();
		final net.runelite.asm.pool.Method draw = inject.toVanilla(deob
				.findClass("Entity")
				.findMethod("draw")
		).getPoolMethod();

		final Method drawTile = inject.toVanilla(deob
			.findClass("Scene")
			.findMethod("drawTile")
		);

		Instructions ins = drawTile.getCode().getInstructions();
		for (ListIterator<Instruction> iterator = ins.listIterator(); iterator.hasNext(); )
		{
			Instruction i = iterator.next();
			if (i instanceof InvokeVirtual)
			{
				if (((InvokeVirtual) i).getMethod().equals(draw))
				{
					iterator.set(new InvokeStatic(ins, RENDERDRAW));
					log.debug("Replaced method call at {}", i);
					++replaced;
				}
			}
		}

		if (replaced != EXPECTED)
			throw new Injexception("Didn't replace the expected amount of method calls");
	}
}
