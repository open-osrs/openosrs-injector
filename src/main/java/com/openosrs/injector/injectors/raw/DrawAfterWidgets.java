package com.openosrs.injector.injectors.raw;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import static com.openosrs.injector.injection.InjectData.HOOKS;
import com.openosrs.injector.injectors.AbstractInjector;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Method;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.Label;
import net.runelite.asm.attributes.code.instruction.types.JumpingInstruction;
import net.runelite.asm.attributes.code.instruction.types.PushConstantInstruction;
import net.runelite.asm.attributes.code.instructions.GetStatic;
import net.runelite.asm.attributes.code.instructions.IMul;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.signature.Signature;

public class DrawAfterWidgets extends AbstractInjector
{
	public DrawAfterWidgets(InjectData inject)
	{
		super(inject);
	}

	public void inject() throws Injexception
	{
		/*
		 * This call has to be injected using raw injection because the
		 * drawWidgets method gets inlined in some revisions. If it wouldn't be,
		 * mixins would be used to add the call to the end of drawWidgets.

		 * --> This hook depends on the positions of "if (535573958 * kl != -1)" and "jz.db();".


		 * Revision 180 - client.gs():
		 * ______________________________________________________

		 * 	@Export("drawLoggedIn")
		 * 	final void drawLoggedIn() {
		 * 		if(rootInterface != -1) {
		 * 			ClientPreferences.method1809(rootInterface);
		 * 		}

		 * 		int var1;
		 * 		for(var1 = 0; var1 < rootWidgetCount; ++var1) {
		 * 			if(__client_od[var1]) {
		 * 				__client_ot[var1] = true;
		 * 			}

		 * 			__client_oq[var1] = __client_od[var1];
		 * 			__client_od[var1] = false;
		 * 		}

		 * 		__client_oo = cycle;
		 * 		__client_lq = -1;
		 * 		__client_ln = -1;
		 * 		UserComparator6.__fg_jh = null;
		 * 		if(rootInterface != -1) {
		 * 			rootWidgetCount = 0;
		 * 			Interpreter.method1977(rootInterface, 0, 0, SoundCache.canvasWidth, Huffman.canvasHeight, 0, 0, -1);
		 * 		}

		 * 	< --  here appearantly

		 * 		Rasterizer2D.Rasterizer2D_resetClip();
		 * ______________________________________________________
		 */

		boolean injected = false;

		Method noClip = InjectUtil.findStaticMethod(inject, "Rasterizer2D_resetClip", "Rasterizer2D", null); // !!!!!

		if (noClip == null)
		{
			throw new Injexception("Mapped method \"Rasterizer2D_resetClip\" could not be found.");
		}

		net.runelite.asm.pool.Method poolNoClip = noClip.getPoolMethod();

		for (ClassFile c : inject.getVanilla())
		{
			for (Method m : c.getMethods())
			{
				if (m.getCode() == null)
				{
					continue;
				}

				Instructions instructions = m.getCode().getInstructions();

				Set<Label> labels = new HashSet<>();

				// Let's find "invokestatic <some class>.noClip()" and its label
				ListIterator<Instruction> labelIterator = instructions.listIterator();
				while (labelIterator.hasNext())
				{
					Instruction i = labelIterator.next();

					if (!(i instanceof InvokeStatic))
					{
						continue;
					}

					InvokeStatic is = (InvokeStatic) i;

					if (!is.getMethod().equals(poolNoClip))
					{
						continue;
					}

					labelIterator.previous();
					Instruction i2 = labelIterator.previous();
					labelIterator.next();
					labelIterator.next();

					// Find the label that marks the code path for the instruction
					if (!(i2 instanceof Label))
					{
						continue;
					}

					// There can be several noClip invocations in a method, so let's catch them all
					labels.add((Label) i2);
				}

				if (labels.isEmpty())
				{
					// If we get here, we're either in the wrong method
					// or Jagex has removed the "if (535573958 * kl != -1)"
						log.debug("Could not find the label for jumping to the " + noClip + " call in " + m);
					continue;
				}

				Set<Label> labelsToInjectAfter = new HashSet<>();

				ListIterator<Instruction> jumpIterator = instructions.listIterator();
				while (jumpIterator.hasNext())
				{
					Instruction i = jumpIterator.next();

					if (!(i instanceof JumpingInstruction))
					{
						continue;
					}

					JumpingInstruction ji = (JumpingInstruction) i;

					Label label = null;

					for (Label l : labels)
					{
						if (ji.getJumps().contains(l))
						{
							label = l;
							break;
						}
					}

					if (label == null)
					{
						continue;
					}

					jumpIterator.previous();

					Set<Instruction> insns = new HashSet<>();
					insns.add(jumpIterator.previous());
					insns.add(jumpIterator.previous());
					insns.add(jumpIterator.previous());
					insns.add(jumpIterator.previous());

					// Get the iterator back to i's position
					jumpIterator.next();
					jumpIterator.next();
					jumpIterator.next();
					jumpIterator.next();
					jumpIterator.next();

					/*
						Check that these instruction types are passed into the if-statement:

						ICONST_M1
						GETSTATIC client.kr : I
						LDC 634425425
						IMUL

						We cannot depend on the order of these because of the obfuscation,
						so let's make it easier by just checking that they are there.
					 */
					if (insns.stream().filter(i2 -> i2 instanceof PushConstantInstruction).count() != 2
						|| insns.stream().filter(i2 -> i2 instanceof IMul).count() != 1
						|| insns.stream().filter(i2 -> i2 instanceof GetStatic).count() != 1)
					{
						continue;
					}

					// At this point, we have found the real injection point
					labelsToInjectAfter.add(label);
				}

				for (Label l : labelsToInjectAfter)
				{
					InvokeStatic invoke = new InvokeStatic(instructions,
						new net.runelite.asm.pool.Method(
							new net.runelite.asm.pool.Class(HOOKS),
							"drawAfterWidgets",
							new Signature("()V")
						)
					);

					instructions.addInstruction(instructions.getInstructions().indexOf(l) + 1, invoke);

					log.debug("injectDrawAfterWidgets injected a call after " + l);

					injected = true;
				}
			}
		}

		if (!injected)
		{
			throw new Injexception("injectDrawAfterWidgets failed to inject!");
		}
	}
}
