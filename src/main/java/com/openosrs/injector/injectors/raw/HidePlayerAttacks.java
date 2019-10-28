package com.openosrs.injector.injectors.raw;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injection;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.injectors.AbstractInjector;
import com.openosrs.injector.injectors.Injector;
import java.util.Iterator;
import java.util.ListIterator;
import net.runelite.asm.Method;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.Label;
import net.runelite.asm.attributes.code.instruction.types.ComparisonInstruction;
import net.runelite.asm.attributes.code.instruction.types.JumpingInstruction;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.BiPush;
import net.runelite.asm.attributes.code.instructions.GetStatic;
import net.runelite.asm.attributes.code.instructions.IAnd;
import net.runelite.asm.attributes.code.instructions.IfACmpEq;
import net.runelite.asm.attributes.code.instructions.IfACmpNe;
import net.runelite.asm.attributes.code.instructions.IfICmpNe;
import net.runelite.asm.attributes.code.instructions.IfNe;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.pool.Field;

public class HidePlayerAttacks extends AbstractInjector
{
	public HidePlayerAttacks(InjectData inject)
	{
		super(inject);
	}

	public void inject() throws Injexception
	{
		final Method addPlayerOptions = InjectUtil.findStaticMethod(inject, "addPlayerToMenu");
		final net.runelite.asm.pool.Method shouldHideAttackOptionFor = inject.getVanilla().findClass("client").findMethod("shouldHideAttackOptionFor").getPoolMethod();

		injectHideAttack(addPlayerOptions, shouldHideAttackOptionFor);
		injectHideCast(addPlayerOptions, shouldHideAttackOptionFor);
	}

	private void injectHideAttack(Method addPlayerOptions, net.runelite.asm.pool.Method shouldHideAttackOptionFor) throws Injexception
	{
		final Field AttackOption_hidden = InjectUtil.findField(inject, "AttackOption_hidden", "AttackOption").getPoolField();
		final Field attackOption = InjectUtil.findField(inject, "playerAttackOption", "Client").getPoolField();

		// GETSTATIC					GETSTATIC
		// GETSTATIC					GETSTATIC
		// IFACMPEQ -> label continue	IFACMPNE -> label whatever lets carry on
		//								MORE OBFUSCATION

		int injectIdx = -1;
		Instruction labelIns = null;
		Label label = null;

		Instructions ins = addPlayerOptions.getCode().getInstructions();
		Iterator<Instruction> iterator = ins.getInstructions().iterator();
		while (iterator.hasNext())
		{
			Instruction i = iterator.next();
			if (!(i instanceof GetStatic))
			{
				continue;
			}

			Field field = ((GetStatic) i).getField();
			if (!field.equals(AttackOption_hidden) && !field.equals(attackOption))
			{
				continue;
			}

			i = iterator.next();
			if (!(i instanceof GetStatic))
			{
				continue;
			}

			field = ((GetStatic) i).getField();
			if (!field.equals(AttackOption_hidden) && !field.equals(attackOption))
			{
				continue;
			}

			i = iterator.next();
			if (!(i instanceof ComparisonInstruction && i instanceof JumpingInstruction))
			{
				log.info("You're not supposed to see this lol");
				continue;
			}

			if (i instanceof IfACmpEq)
			{
				injectIdx = ins.getInstructions().indexOf(i) + 1;
				label = ((IfACmpEq) i).getJumps().get(0);
			}
			else if (i instanceof IfACmpNe)
			{
				injectIdx = ins.getInstructions().indexOf(((IfACmpNe) i).getJumps().get(0)) + 1;
				// We're gonna have to inject a extra label
				labelIns = iterator.next();
			}

			break;
		}

		if (injectIdx <= 0 || label == null && labelIns == null)
		{
			throw new Injexception("HidePlayerAttacks failed");
		}

		// Load the player
		ALoad i1 = new ALoad(ins, 0);
		// Get the boolean
		InvokeStatic i2 = new InvokeStatic(ins, shouldHideAttackOptionFor);

		ins.addInstruction(injectIdx, i1);
		ins.addInstruction(injectIdx + 1, i2);

		if (label == null)
		{
			label = ins.createLabelFor(labelIns);
			ins.rebuildLabels();
			injectIdx = ins.getInstructions().indexOf(i2) + 1;
		}

		// Compare n such
		IfNe i3 = new IfNe(ins, label);

		ins.addInstruction(injectIdx, i3);
	}

	private void injectHideCast(Method addPlayerOptions, net.runelite.asm.pool.Method shouldHideAttackOptionFor) throws Injexception
	{
		// LABEL before
		// BIPUSH 8
		// LDC (garbage)
		// GETSTATIC selectedSpellFlags
		// IMUL
		// BIPUSH 8
		// IAND
		// IF_ICMPNE -> skip adding option
		//
		// <--- Inject call here
		// <--- Inject comparison here (duh)
		//
		// add option n such

		Instructions ins = addPlayerOptions.getCode().getInstructions();
		log.info(String.valueOf(ins.getInstructions().size()));
		ListIterator<Instruction> iterator = ins.getInstructions().listIterator();
		while (iterator.hasNext())
		{
			Instruction i = iterator.next();
			if (!(i instanceof BiPush) || (byte) ((BiPush) i).getConstant() != 8)
			{
				continue;
			}

			i = iterator.next();
			while (!(i instanceof BiPush) || (byte) ((BiPush) i).getConstant() != 8)
			{
				i = iterator.next();
			}

			i = iterator.next();
			if (!(i instanceof IAnd))
			{
				throw new Injexception("Yikes I didn't expect this");
			}

			i = iterator.next();
			if (!(i instanceof IfICmpNe))
			{
				continue;
			}

			Label target = ((IfICmpNe) i).getJumps().get(0);

			// Load the player
			ALoad i1 = new ALoad(ins, 0);
			// Get the boolean
			InvokeStatic i2 = new InvokeStatic(ins, shouldHideAttackOptionFor);
			// Compare n such
			IfNe i3 = new IfNe(ins, target);

			iterator.add(i1);
			iterator.add(i2);
			iterator.add(i3);
			return;
		}
	}
}
