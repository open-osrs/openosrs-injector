//package com.openosrs.injector.injectors.raw;

//import com.openosrs.injector.InjectUtil;
//import com.openosrs.injector.Injexception;
//import com.openosrs.injector.injection.InjectData;
//import static com.openosrs.injector.injection.InjectData.HOOKS;
//import com.openosrs.injector.injectors.AbstractInjector;
//import java.util.List;
//import java.util.ListIterator;
//import net.runelite.asm.ClassFile;
//import net.runelite.asm.ClassGroup;
//import net.runelite.asm.Method;
//import net.runelite.asm.Type;
//import net.runelite.asm.attributes.code.Instruction;
//import net.runelite.asm.attributes.code.Instructions;
//import net.runelite.asm.attributes.code.Label;
//import net.runelite.asm.attributes.code.instruction.types.JumpingInstruction;
//import net.runelite.asm.attributes.code.instruction.types.PushConstantInstruction;
//import net.runelite.asm.attributes.code.instructions.GetStatic;
//import net.runelite.asm.attributes.code.instructions.IStore;
//import net.runelite.asm.attributes.code.instructions.IfEq;
//import net.runelite.asm.attributes.code.instructions.IfNe;
//import net.runelite.asm.attributes.code.instructions.InvokeStatic;
//import net.runelite.asm.execution.Execution;
//import net.runelite.asm.execution.InstructionContext;
//import net.runelite.asm.pool.Class;
//import net.runelite.asm.pool.Field;
//import net.runelite.asm.signature.Signature;

//public class DrawMenu extends AbstractInjector
//{
	//private static final net.runelite.asm.pool.Method HOOK = new net.runelite.asm.pool.Method(
		//new Class(HOOKS),
		//"drawMenu",
		//new Signature("()Z")
	//);
	//private static final int MENU_COLOR = 0x5d5447;

	//public DrawMenu(InjectData inject)
	//{
		//super(inject);
	//}

	//public void inject() throws Injexception
	//{
		///*
		 //* Label Getstatic client.isMenuOpen
		 //* Ifne -> Label Drawmenu
		 //* Jump -> Label Drawtext
		 //*
		 //* Label drawtext
		 //* Ldc xxx
		 //* Getstatic client. something with viewport size?
		 //* Imul
		 //* Iconst_m1
		 //* Ifne -> Label after draw menu <- info we need
		 //* Getstatic / LDC (same getstatic and LDC before)
		 //* Getstatic / LDC
		 //*/
		//final ClassFile deobClient = inject.getDeobfuscated().findClass("Client");
		//final ClassGroup vanilla = inject.getVanilla();

		//final Field isMenuOpen = inject.toVanilla(deobClient.findField("isMenuOpen")).getPoolField();
		//final Method drawLoggedIn = inject.toVanilla(deobClient.findMethod("drawLoggedIn"));

		//final Instructions inst = drawLoggedIn.getCode().getInstructions();

		//boolean foundCol = false, foundEnd = false;
		//int injIdx = -1;
		//final ListIterator<Instruction> it = inst.listIterator();
		//while (it.hasNext())
		//{
			//Instruction i = it.next();
			//if (!foundCol &&
				//(i instanceof PushConstantInstruction) &&
				//((PushConstantInstruction) i).getConstant().equals(MENU_COLOR))
			//{
				//foundCol = true;
				//injIdx = it.nextIndex();
			//}
			//else if (!foundEnd &&
				//(i instanceof PushConstantInstruction) &&
				//((PushConstantInstruction) i).getConstant().equals(0))
			//{
				//i = it.next();
				//if (!(i instanceof IStore))
				//{
					//continue;
				//}

				//int varIdx = ((IStore) i).getVariableIndex();
				//i = it.next();

				//if (!(i instanceof JumpingInstruction))
				//{
					//continue;
				//}

				//List<Label> jumps = ((JumpingInstruction) i).getJumps();
				//if (jumps.size() > 1)
				//{
					//continue;
				//}
				//
				//Instruction afterLabel = inst.
			//}
		//}


		//final Execution ex = new Execution(vanilla);
		//// Static step makes static methods not count as methods
		//ex.staticStep = true;
		//ex.noExceptions = true;
		//ex.addMethod(drawLoggedIn);

		//final Instruction
		//ex.addExecutionVisitor((InstructionContext ic) ->
		//{
			//Instruction i = ic.getInstruction();
			//if (i instanceof PushConstantInstruction)
			//{
				//if (((PushConstantInstruction) i).getConstant().equals(MENU_COLOR))
				//{
					//injIdx
				//}
			//}
		//});
		//ex.run();

		//final Instructions ins = drawLoggedIn.getCode().getInstructions();
		//ListIterator<Instruction> it = ins.getInstructions().listIterator();
		//int injectIndex = -1;
		//Label after = null;
		//boolean foundBefore = false;
		//boolean foundAfter = false;

		//while (it.hasNext())
		//{
			//Instruction i = it.next();

			//if (!(i instanceof GetStatic) && !(i instanceof InvokeStatic))
			//{
				//continue;
			//}

			//if (!foundBefore && i instanceof GetStatic)
			//{
				//if (!((GetStatic) i).getField().equals(isMenuOpen))
				//{
					//continue;
				//}

				//i = it.next();
				//if (!(i instanceof IfEq) && !(i instanceof IfNe))
				//{
					//continue;
				//}

				//if (i instanceof IfEq)
				//{
					//injectIndex = it.nextIndex();
				//}
				//else
				//{
					//injectIndex = ins.getInstructions().indexOf(((IfNe) i).getJumps().get(0)) + 1;
				//}
				//foundBefore = true;
			//}
			//else if (!foundAfter && i instanceof InvokeStatic
				//&& ((InvokeStatic) i).getMethod().equals("topLeftText"))
			//{
				//i = it.next();
				//assert i instanceof JumpingInstruction;
				//after = ((JumpingInstruction) i).getJumps().get(0);
				//foundAfter = true;
			//}

			//if (foundBefore && foundAfter)
			//{
				//break;
			//}
		//}

		//if (!foundBefore || !foundAfter || injectIndex == -1)
		//{

		//}

		//ins.addInstruction(injectIndex, new IfNe(ins, after));
		//ins.addInstruction(injectIndex, new InvokeStatic(ins, HOOK));
		//log.info("Injected drawmenu hook in {} at index {}", "", injectIndex);


	//}
//}
