package com.openosrs.injector.injectors;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.rsapi.RSApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Provider;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.attributes.Code;
import net.runelite.asm.attributes.annotation.Annotation;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.InstructionType;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instruction.types.InvokeInstruction;
import net.runelite.asm.attributes.code.instruction.types.ReturnInstruction;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.attributes.code.instructions.InvokeVirtual;
import net.runelite.asm.signature.Signature;

public class InjectHookMethod extends AbstractInjector
{
	private static final String HOOKS = "net/runelite/client/callback/Hooks";
	private static final Type METHODHOOK = new Type("Lnet/runelite/api/mixins/MethodHook;");
	private final Map<Provider<ClassFile>, List<ClassFile>> mixinTargets;

	private int injected = 0;

	InjectHookMethod(final InjectData inject, final Map<Provider<ClassFile>, List<ClassFile>> mixinTargets)
	{
		super(inject);
		this.mixinTargets = mixinTargets;
	}

	@Override
	public void inject() throws Injexception
	{
		for (Map.Entry<Provider<ClassFile>, List<ClassFile>> entry : mixinTargets.entrySet())
		{
			injectMethods(entry.getKey(), entry.getValue());
		}

		log.info("Injected {} method hooks", injected);
	}

	private void injectMethods(Provider<ClassFile> mixinProvider, List<ClassFile> targetClasses) throws Injexception
	{
		final ClassFile mixinClass = mixinProvider.get();

		for (ClassFile targetClass : targetClasses)
		{
			for (Method mixinMethod : mixinClass.getMethods())
			{
				final Annotation methodHook = mixinMethod.getAnnotations().find(METHODHOOK);
				if (methodHook == null)
				{
					continue;
				}

				final String hookName = methodHook.getElement().getString();
				final boolean end = methodHook.getElements().size() == 2 && methodHook.getElements().get(1).getValue().equals(true);

				final ClassFile deobTarget = inject.toDeob(targetClass.getName());
				final Method deobMethod;

				if (mixinMethod.isStatic())
				{
					deobMethod = InjectUtil.findStaticMethod(deobTarget.getGroup(), hookName);
				}
				else
				{
					deobMethod = InjectUtil.findMethodDeep(deobTarget, hookName);
				}

				assert mixinMethod.isStatic() == deobMethod.isStatic() : "Mixin method isn't static but deob has a static method named the same as the hook, and I was too lazy to do something about this bug";

				inject(mixinMethod, deobMethod, hookName, end);
				++injected;
			}
		}
	}

	private void inject(Method mixinMethod, Method targetMethod, String name, boolean end) throws Injexception
	{
		// Method is hooked
		// Find equivalent method in vanilla, and insert callback at the beginning
		ClassFile deobClass = targetMethod.getClassFile();
		String obfuscatedMethodName = InjectUtil.getObfuscatedName(targetMethod);
		String obfuscatedClassName = InjectUtil.getObfuscatedName(deobClass);

		assert obfuscatedClassName != null : "hook on method in class with no obfuscated name";
		assert obfuscatedMethodName != null : "hook on method with no obfuscated name";

		Signature obfuscatedSignature = targetMethod.getObfuscatedSignature();

		ClassFile vanillaClass = inject.toVanilla(deobClass);
		Method vanillaMethod = vanillaClass.findMethod(obfuscatedMethodName, obfuscatedSignature);
		assert targetMethod.isStatic() == vanillaMethod.isStatic();

		// Insert instructions at beginning of method
		injectHookMethod(mixinMethod, name, end, targetMethod, vanillaMethod, false);
	}

	private void injectHookMethod(Method hookMethod, String hookName, boolean end, Method deobMethod, Method vanillaMethod, boolean useHooks) throws Injexception
	{
		Code code = vanillaMethod.getCode();
		if (code == null)
		{
			log.warn(vanillaMethod + " code is null");
			return;
		}

		Instructions instructions = code.getInstructions();

		Signature.Builder builder = new Signature.Builder()
			.setReturnType(Type.VOID); // Hooks always return void

		for (Type type : deobMethod.getDescriptor().getArguments())
		{
			builder.addArgument(new Type("L" + RSApi.API_BASE + type.getInternalName() + ";"));
		}

		assert deobMethod.isStatic() == vanillaMethod.isStatic();

		boolean modifiedSignature = false;
		if (!deobMethod.isStatic() && useHooks)
		{
			// Add variable to signature
			builder.addArgument(0, inject.deobfuscatedTypeToApiType(new Type(deobMethod.getClassFile().getName())));
			modifiedSignature = true;
		}

		Signature signature = builder.build();

		List<Integer> insertIndexes = findHookLocations(hookName, end, vanillaMethod);
		insertIndexes.sort((a, b) -> Integer.compare(b, a));

		for (int insertPos : insertIndexes)
		{
			if (!deobMethod.isStatic())
			{
				instructions.addInstruction(insertPos++, new ALoad(instructions, 0));
			}

			int signatureStart = modifiedSignature ? 1 : 0;
			int index = deobMethod.isStatic() ? 0 : 1; // current variable index

			for (int i = signatureStart; i < signature.size(); ++i)
			{
				Type type = signature.getTypeOfArg(i);

				Instruction load = InjectUtil.createLoadForTypeIndex(instructions, type, index);
				instructions.addInstruction(insertPos++, load);

				index += type.getSize();
			}

			InvokeInstruction invoke;

			// use old Hooks callback
			if (useHooks)
			{
				// Invoke callback
				invoke = new InvokeStatic(instructions,
					new net.runelite.asm.pool.Method(
						new net.runelite.asm.pool.Class(HOOKS),
						hookName,
						signature
					)
				);
			}
			else
			{
				// Invoke methodhook
				assert hookMethod != null;

				if (vanillaMethod.isStatic())
				{
					invoke = new InvokeStatic(instructions,
						new net.runelite.asm.pool.Method(
							new net.runelite.asm.pool.Class("client"), // Static methods are in client
							hookMethod.getName(),
							signature
						)
					);
				}
				else
				{
					// otherwise invoke member function
					//instructions.addInstruction(insertPos++, new ALoad(instructions, 0));
					invoke = new InvokeVirtual(instructions,
						new net.runelite.asm.pool.Method(
							new net.runelite.asm.pool.Class(vanillaMethod.getClassFile().getName()),
							hookMethod.getName(),
							hookMethod.getDescriptor()
						)
					);
				}
			}

			instructions.addInstruction(insertPos++, (Instruction) invoke);
		}

		log.debug("Injected method hook {} in {} with {} args: {}",
			hookName, vanillaMethod, signature.size(),
			signature.getArguments());
	}

	private List<Integer> findHookLocations(String hookName, boolean end, Method vanillaMethod) throws Injexception
	{
		Instructions instructions = vanillaMethod.getCode().getInstructions();

		if (end)
		{
			// find return
			List<Instruction> returns = instructions.getInstructions().stream()
				.filter(i -> i instanceof ReturnInstruction)
				.collect(Collectors.toList());
			List<Integer> indexes = new ArrayList<>();

			for (Instruction ret : returns)
			{
				int idx = instructions.getInstructions().indexOf(ret);
				assert idx != -1;
				indexes.add(idx);
			}

			return indexes;
		}

		if (!vanillaMethod.getName().equals("<init>"))
		{
			return Arrays.asList(0);
		}

		// Find index after invokespecial
		for (int i = 0; i < instructions.getInstructions().size(); ++i)
		{
			Instruction in = instructions.getInstructions().get(i);

			if (in.getType() == InstructionType.INVOKESPECIAL)
			{
				return Arrays.asList(i + 1); // one after
			}
		}

		throw new IllegalStateException("constructor with no invokespecial");
	}
}
