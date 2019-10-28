package com.openosrs.injector.injectors.raw;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.injectors.AbstractInjector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.asm.Field;
import net.runelite.asm.Type;
import net.runelite.asm.attributes.Code;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.InstructionType;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instruction.types.LVTInstruction;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.ArrayStore;
import net.runelite.asm.attributes.code.instructions.GetField;
import net.runelite.asm.attributes.code.instructions.GetStatic;
import net.runelite.asm.attributes.code.instructions.IALoad;
import net.runelite.asm.attributes.code.instructions.IAStore;
import net.runelite.asm.attributes.code.instructions.ILoad;
import net.runelite.asm.attributes.code.instructions.IOr;
import net.runelite.asm.attributes.code.instructions.ISub;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.attributes.code.instructions.LDC;
import net.runelite.asm.execution.Execution;
import net.runelite.asm.execution.InstructionContext;
import net.runelite.asm.pool.Class;
import net.runelite.asm.pool.Method;
import net.runelite.asm.signature.Signature;

public class RasterizerHook extends AbstractInjector
{
	// TODO: Should probably make this better

	private static final int val = 0xff000000;
	private static final Class R3D = new Class("Rasterizer3D");
	private static final Class FONT = new Class("AbstractFont");
	private static final Class R2D = new Class("Rasterizer2D");
	private static final Class SPRITE = new Class("Sprite");
	private net.runelite.asm.pool.Field R2D_PIXELS;

	private static final Method r3d_vert = new Method(R3D, "Rasterizer3D_vertAlpha", new Signature("([IIIIIIII)V"));
	private static final Method r3d_horiz = new Method(R3D, "Rasterizer3D_horizAlpha", new Signature("([IIIIII)V"));
	private static final net.runelite.asm.pool.Field r3d_field = new net.runelite.asm.pool.Field(R3D, "Rasterizer3D_alpha", Type.INT);

	private static final Method font_alpha = new Method(FONT, "AbstractFont_placeGlyphAlpha", new Signature("([I[BIIIIIIII)V"));

	private static final Method circle_alpha = new Method(R2D, "Rasterizer2D_drawCircleAlpha", new Signature("(IIIII)V"));
	private static final Method line_alpha = new Method(R2D, "Rasterizer2D_drawHorizontalLineAlpha", new Signature("(IIIII)V"));
	private static final Method line_alpha2 = new Method(R2D, "Rasterizer2D_drawVerticalLineAlpha", new Signature("(IIIII)V"));
	private static final Method fill_rect_alpha = new Method(R2D, "Rasterizer2D_fillRectangleAlpha", new Signature("(IIIIII)V"));

	private static final Method sprite_alpha1 = new Method(SPRITE, "Sprite_drawTransparent", new Signature("([I[IIIIIIIII)V"));
	private static final Method sprite_alpha2 = new Method(SPRITE, "Sprite_drawTransScaled", new Signature("([I[IIIIIIIIIIII)V"));

	private static final String font = "AbstractFont_placeGlyph";
	private static final String rast3D = "Rasterizer3D_iDontKnow";
	private static final String rast3D2 = "Rasterizer3D_textureAlpha";
	private static final String sprite = "Sprite_draw";
	private static final String sprite2 = "Sprite_drawScaled";
	private static final String sprite3 = "Sprite_drawTransOverlay";
	private static final String sprite4 = "Sprite_drawTransBg";
	private static final String indexedSprite = "IndexedSprite_something";
	private static final String indexedSprite2 = "IndexedSprite_two";

	private static final net.runelite.asm.pool.Method drawAlpha = new net.runelite.asm.pool.Method(
		new Class("client"),
		"drawAlpha",
		new Signature("([IIII)V")
	);

	private int drawAlphaCount = 0;
	private int orCount = 0;

	public RasterizerHook(InjectData inject) throws Injexception
	{
		super(inject);
	}

	public void inject() throws Injexception
	{
		R2D_PIXELS = InjectUtil.findStaticField(inject, "Rasterizer2D_pixels", R2D.getName(), new Type("[I")).getPoolField();

		injectDrawAlpha();

		runVars();

		run();
	}

	private void injectDrawAlpha() throws Injexception
	{
		final Field field = InjectUtil.findStaticField(inject, r3d_field);
		runR3DAlpha(r3d_horiz, field, 15);
		runR3DAlpha(r3d_vert, field, 12);
		runFontAlpha(font_alpha, 1, 9);

		runAlpha(circle_alpha, 2, 4);
		runAlpha(line_alpha, 1, 4);
		runAlpha(line_alpha2, 1, 4);
		runAlpha(fill_rect_alpha, 1, 5);
		runAlpha(sprite_alpha1, 1, 9, 0);
		runAlpha(sprite_alpha2, 1, 12, 0);
	}

	private void runR3DAlpha(Method pool, net.runelite.asm.Field field, int req) throws Injexception
	{
		net.runelite.asm.Method method = InjectUtil.findStaticMethod(inject, pool);
		Instructions ins = method.getCode().getInstructions();
		int varIdx = 0; // This is obviously dumb but I cba making this better
		int added = 0;

		List<Integer> indices = new ArrayList<>();
		for (Instruction i : method.findLVTInstructionsForVariable(varIdx))
		{
			indices.add(ins.getInstructions().indexOf(i));
		}

		if (indices.isEmpty())
		{
			throw new Injexception("Couldn't find hook location in " + pool.toString());
		}

		for (int i : indices)
		{
			for (int codeIndex = i + added; codeIndex < ins.getInstructions().size(); codeIndex++)
			{
				if (ins.getInstructions().get(codeIndex) instanceof IAStore)
				{
					ins.getInstructions().set(codeIndex, new InvokeStatic(ins, drawAlpha));

					ins.getInstructions().add(codeIndex, new ISub(ins, InstructionType.ISUB));
					ins.getInstructions().add(codeIndex, new GetStatic(ins, field.getPoolField()));
					ins.getInstructions().add(codeIndex, new LDC(ins, 255));
					added++;
					drawAlphaCount++;
					break;
				}
			}
		}

		if (added != req)
		{
			throw new Injexception("Not enough drawAlphas injected into " + pool.toString() + " (" + added + "/" + req + ")");
		}
	}

	private void runFontAlpha(Method pool, int req, int extraArg) throws Injexception
	{
		net.runelite.asm.Method meth = InjectUtil.findStaticMethod(inject, pool);
		Instructions ins = meth.getCode().getInstructions();
		int varIdx = 0; // This is obviously dumb but I cba making this better
		int added = 0;

		List<Integer> indices = new ArrayList<>();
		for (Instruction i : meth.findLVTInstructionsForVariable(varIdx))
		{
			indices.add(ins.getInstructions().indexOf(i));
		}

		if (indices.isEmpty())
		{
			throw new Injexception("Couldn't find hook location in " + pool.toString());
		}

		for (int i : indices)
		{
			for (int codeIndex = i + added; codeIndex < ins.getInstructions().size(); codeIndex++)
			{
				if (ins.getInstructions().get(codeIndex) instanceof IAStore)
				{
					ins.getInstructions().set(codeIndex, new InvokeStatic(ins, drawAlpha));

					ins.getInstructions().add(codeIndex, new ISub(ins, InstructionType.ISUB));
					ins.getInstructions().add(codeIndex, new ILoad(ins, extraArg));
					ins.getInstructions().add(codeIndex, new LDC(ins, 255));
					added++;
					drawAlphaCount++;
					break;
				}
			}
		}

		if (added != req)
		{
			throw new Injexception("Not enough drawAlphas injected into " + pool.toString() + " (" + added + "/" + req + ")");
		}
	}

	private void runAlpha(Method pool,  int req, int extraArg) throws Injexception
	{
		runAlpha(pool, req, extraArg, -1);
	}

	private void runAlpha(Method pool, int req, int extraArg, int varIndex) throws Injexception
	{
		net.runelite.asm.Method meth = InjectUtil.findStaticMethod(inject, pool);

		Code code = meth.getCode();
		Instructions ins = code.getInstructions();
		int added = 0;

		List<Integer> indices = new ArrayList<>();
		for (Instruction i : ins.getInstructions())
		{
			if (!(i instanceof IALoad) && !(i instanceof GetField) && !(i instanceof ALoad))
			{
				continue;
			}

			if (i instanceof GetField)
			{
				if (((GetField) i).getField().equals(R2D_PIXELS))
				{
					indices.add(ins.getInstructions().indexOf(i));
				}
			}
			else if ((i instanceof ALoad) && varIndex >= 0 && ((LVTInstruction) i).getVariableIndex() == varIndex)
			{
				indices.add(ins.getInstructions().indexOf(i));
			}
			else if (varIndex == -1)
			{
				indices.add(ins.getInstructions().indexOf(i));
			}
		}

		if (indices.isEmpty())
		{
			throw new Injexception("Couldn't find hook location in " + pool.toString());
		}

		final int oldCount = drawAlphaCount;

		for (int i : indices)
		{
			for (int codeIndex = i + added; codeIndex < ins.getInstructions().size(); codeIndex++)
			{
				if (ins.getInstructions().get(codeIndex) instanceof IAStore)
				{
					ins.getInstructions().set(codeIndex, new InvokeStatic(ins, drawAlpha));
					if (extraArg != -1)
					{
						ins.getInstructions().add(codeIndex, new ILoad(ins, extraArg));
						added++;
					}
					drawAlphaCount++;
					break;
				}
			}
		}

		if (drawAlphaCount - oldCount > req)
		{
			throw new Injexception("Too many drawAlpha's were injected into " + pool.toString());
		}
	}

	private void runVars() throws Injexception
	{
		runOnMethodWithVar(rast3D, 0, 36);
		runOnMethodWithVar(rast3D2, 0, 36);
		// 36 expected
		runOnMethodWithVar(font, 0, 5);
		// 5 expected
		runOnMethodWithVar(sprite, 1, 5);
		runOnMethodWithVar(sprite2, 0, 1);
		runOnMethodWithVar(sprite3, 0, 1);
		runOnMethodWithVar(sprite4, 0, 5);
		// 12 expected
		runOnMethodWithVar(indexedSprite, 0, 1);
		runOnMethodWithVar(indexedSprite2, 0, 5);
		// 6 expected
	}

	private void run() throws Injexception
	{
		final int startCount = orCount; // Cause you can't just count shit ty

		Execution ex = new Execution(inject.getVanilla());
		ex.populateInitialMethods();

		Set<Instruction> done = new HashSet<>();
		ex.addExecutionVisitor((InstructionContext ic) ->
		{
			Instruction i = ic.getInstruction();
			Instructions ins = i.getInstructions();
			Code code = ins.getCode();
			net.runelite.asm.Method method = code.getMethod();

			if (!(i instanceof IAStore))
			{
				return;
			}

			if (!done.add(i))
			{
				return;
			}

			ArrayStore as = (ArrayStore) i;
			Field fieldBeingSet = as.getMyField(ic);

			if (fieldBeingSet == null)
			{
				return;
			}

			if (!fieldBeingSet.getPoolField().equals(R2D_PIXELS))
			{
				return;
			}

			int index = ins.getInstructions().indexOf(i);

			if (!(ins.getInstructions().get(index - 1) instanceof ILoad) && !ic.getPops().get(0).getValue().isUnknownOrNull())
			{
				if ((int) ic.getPops().get(0).getValue().getValue() == 0)
				{
					log.debug("Didn't add hook in method {}.{}. {} added, {} total, value 0", method.getClassFile().getClassName(), method.getName(), orCount - startCount, orCount);
					return;
				}
			}

			ins.getInstructions().add(index, new IOr(ins, InstructionType.IOR)); // Add instructions backwards
			ins.getInstructions().add(index, new LDC(ins, val));
			orCount++;
			log.debug("Added hook in method {}.{}. {} added, {} total", method.getClassFile().getClassName(), method.getName(), orCount - startCount, orCount);
		});

		ex.run();
	}

	private void runOnMethodWithVar(String meth, int varIndex, int req) throws Injexception
	{
		net.runelite.asm.Method method = InjectUtil.findStaticMethod(inject, meth);

		Instructions ins = method.getCode().getInstructions();
		List<Integer> indices = new ArrayList<>();

		for (Instruction i : method.findLVTInstructionsForVariable(varIndex))
		{
			int index = ins.getInstructions().indexOf(i);

			assert index != -1;
			assert ins.getInstructions().get(index + 1) instanceof ILoad; // Index in the array

			indices.add(index);
		}

		int added = 0;
		for (int i : indices)
		{
			for (int codeIndex = i + added; codeIndex < ins.getInstructions().size(); codeIndex++)
			{
				if (ins.getInstructions().get(codeIndex) instanceof IAStore)
				{
					added += 2;
					orCount++;
					ins.addInstruction(codeIndex, new IOr(ins, InstructionType.IOR)); // Add instructions backwards
					ins.addInstruction(codeIndex, new LDC(ins, val));
					break;
				}
			}
		}

		if (added / 2 != req)
		{
			throw new Injexception("Didn't inject the right amount of ors into " + meth);
		}

		log.info("Added {} instructions in {}. {} total", added / 2, meth, orCount);
	}

	public boolean validate()
	{
		return drawAlphaCount + 1 == 35 && orCount + 1 == 125;
	}
}
