package com.openosrs.injector.injectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import javax.inject.Provider;
import lombok.AllArgsConstructor;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Field;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.attributes.Code;
import net.runelite.asm.attributes.annotation.Annotation;
import net.runelite.asm.attributes.code.Instruction;
import net.runelite.asm.attributes.code.Instructions;
import net.runelite.asm.attributes.code.instruction.types.FieldInstruction;
import net.runelite.asm.attributes.code.instruction.types.InvokeInstruction;
import net.runelite.asm.attributes.code.instruction.types.LVTInstruction;
import net.runelite.asm.attributes.code.instruction.types.PushConstantInstruction;
import net.runelite.asm.attributes.code.instruction.types.ReturnInstruction;
import net.runelite.asm.attributes.code.instructions.ALoad;
import net.runelite.asm.attributes.code.instructions.ANewArray;
import net.runelite.asm.attributes.code.instructions.CheckCast;
import net.runelite.asm.attributes.code.instructions.GetField;
import net.runelite.asm.attributes.code.instructions.ILoad;
import net.runelite.asm.attributes.code.instructions.InvokeDynamic;
import net.runelite.asm.attributes.code.instructions.InvokeSpecial;
import net.runelite.asm.attributes.code.instructions.InvokeStatic;
import net.runelite.asm.attributes.code.instructions.Pop;
import net.runelite.asm.attributes.code.instructions.PutField;
import net.runelite.asm.signature.Signature;
import net.runelite.deob.util.JarUtil;

public class MixinInjector extends AbstractInjector
{
	private static final Type COPY = new Type("Lnet/runelite/api/mixins/Copy;");
	private static final Type INJECT = new Type("Lnet/runelite/api/mixins/Inject;");
	private static final Type MIXIN = new Type("Lnet/runelite/api/mixins/Mixin;");
	private static final Type MIXINS = new Type("Lnet/runelite/api/mixins/Mixins;");
	private static final Type REPLACE = new Type("Lnet/runelite/api/mixins/Replace;");
	private static final Type SHADOW = new Type("Lnet/runelite/api/mixins/Shadow;");
	private static final String ASSERTION_FIELD = "$assertionsDisabled";
	private static final String MIXIN_BASE = "net/runelite/mixins/";

	private final Map<String, Field> injectedFields = new HashMap<>();
	private final Map<net.runelite.asm.pool.Field, Field> shadowFields = new HashMap<>();
	private int copied = 0, replaced = 0, injected = 0;

	public MixinInjector(InjectData inject)
	{
		super(inject);
	}

	@Override
	public void inject() throws Injexception
	{
		final Map<Provider<ClassFile>, List<ClassFile>> mixinTargets = initTargets();
		inject(mixinTargets);
	}

	@VisibleForTesting
	void inject(Map<Provider<ClassFile>, List<ClassFile>> mixinTargets) throws Injexception
	{
		for (Map.Entry<Provider<ClassFile>, List<ClassFile>> entry : mixinTargets.entrySet())
		{
			injectFields(entry.getKey(), entry.getValue());
		}

		log.info("Injected {} fields", injectedFields.size());

		for (Map.Entry<Provider<ClassFile>, List<ClassFile>> entry : mixinTargets.entrySet())
		{
			findShadowFields(entry.getKey(), entry.getValue());
		}

		log.info("Shadowed {} fields", shadowFields.size());

		for (Map.Entry<Provider<ClassFile>, List<ClassFile>> entry : mixinTargets.entrySet())
		{
			injectMethods(entry.getKey(), entry.getValue());
		}

		log.info("Injected {}, copied {}, replaced {} methods", injected, copied, replaced);
		inject.runChildInjector(new InjectHook(inject, mixinTargets));

		inject.runChildInjector(new InjectHookMethod(inject, mixinTargets));
	}

	private Map<Provider<ClassFile>, List<ClassFile>> initTargets()
	{
		ImmutableMap.Builder<Provider<ClassFile>, List<ClassFile>> builder = ImmutableMap.builder();

		for (ClassFile mixinClass : inject.getMixins())
		{
			for (Annotation annotation : mixinClass.getAnnotations())
			{
				// If there's multiple mixins we gotta create new copies of the classfiles
				// to make sure we aren't changing code in all classes at once
				if (MIXIN.equals(annotation.getType()))
				{
					final String str = ((org.objectweb.asm.Type) annotation.getElement().getValue()).getInternalName();

					builder.put(
						new Provider<ClassFile>()
						{
							@Override
							public ClassFile get()
							{
								return mixinClass;
							}
						},
						ImmutableList.of(inject.toVanilla(inject.toDeob(str)))
					);
				}
				else if (MIXINS.equals(annotation.getType()))
				{
					final Provider<ClassFile>mixinProvider = new Provider<ClassFile>()
					{
						byte[] bytes = null;

						@Override
						public ClassFile get()
						{
							if (bytes == null)
							{
								bytes = JarUtil.writeClass(mixinClass.getGroup(), mixinClass);
								return mixinClass;
							}
							else
							{
								return JarUtil.loadClass(bytes);
							}
						}
					};

					final List<ClassFile> targetClasses = annotation
						.getElements()
						.stream()
						.map(e -> ((org.objectweb.asm.Type) e.getValue()).getInternalName())
						.map(inject::toDeob)
						.map(inject::toVanilla)
						.collect(ImmutableList.toImmutableList());

					builder.put(mixinProvider, targetClasses);
				}
			}
		}

		return builder.build();
	}

	private void injectFields(Provider<ClassFile> mixinProvider, List<ClassFile> targetClasses) throws Injexception
	{
		final ClassFile mixinClass = mixinProvider.get();

		for (final ClassFile targetClass : targetClasses)
		{
			for (Field field : mixinClass.getFields())
			{
				if (field.getAnnotations().find(INJECT) != null ||
					ASSERTION_FIELD.equals(field.getName()) &&
					targetClass.findField(ASSERTION_FIELD, Type.BOOLEAN) == null)
				{
					Field copy = new Field(targetClass, field.getName(), field.getType());
					copy.setAccessFlags(field.getAccessFlags());
					copy.setPublic();
					copy.setValue(field.getValue());

					for (Annotation annotation : field.getAnnotations())
					{
						if (!annotation.getType().toString().startsWith("Lnet/runelite/api/mixins"))
						{
							copy.getAnnotations().addAnnotation(annotation);
						}
					}

					targetClass.addField(copy);

					if (injectedFields.containsKey(field.getName()) && !ASSERTION_FIELD.equals(field.getName()))
					{
						throw new Injexception("Duplicate field: " + field.getName());
					}

					injectedFields.put(field.getName(), copy);
				}
			}
		}
	}

	private void findShadowFields(Provider<ClassFile> mixinProvider, List<ClassFile> targetClasses) throws Injexception
	{
		final ClassFile mixinClass = mixinProvider.get();

		for (final Field field : mixinClass.getFields())
		{
			Annotation shadow = field.getAnnotations().find(SHADOW);

			if (shadow != null)
			{
				if (!field.isStatic())
				{
					throw new Injexception("Shadowed fields must be static");
				}

				String shadowed = shadow.getElement().getString();

				Field targetField = injectedFields.get(shadowed);
				if (targetField == null)
				{
					targetField = InjectUtil.findStaticField(inject, shadowed, null, InjectUtil.apiToDeob(inject, field.getType()));
				}

				shadowFields.put(field.getPoolField(), targetField);
			}
		}
	}

	private void injectMethods(Provider<ClassFile> mixinProvider, List<ClassFile> targetClasses) throws Injexception
	{
		final ClassFile mixinClass = mixinProvider.get();

		for (ClassFile targetClass : targetClasses)
		{
			// Keeps mappings between methods annotated with @Copy -> the copied method within the vanilla pack
			Map<net.runelite.asm.pool.Method, CopiedMethod> copiedMethods = new HashMap<>();

			// Handle the copy mixins first, so all other mixins know of the copies
			for (Method mixinMethod : mixinClass.getMethods())
			{
				Annotation copyA = mixinMethod.getAnnotations().find(COPY);
				if (copyA == null)
				{
					continue;
				}

				String copiedName = copyA.getElement().getString();
				// The method we're copying, deob
				Method deobSourceMethod;

				if (mixinMethod.isStatic())
				{
					deobSourceMethod = InjectUtil.findStaticMethod(inject.getDeobfuscated(), copiedName, mixinMethod.getDescriptor().rsApiToRsClient());
				}
				else
				{
					deobSourceMethod = InjectUtil.findMethodDeep(inject.toDeob(targetClass.getName()), copiedName, mixinMethod.getDescriptor().rsApiToRsClient());
				}

				if (mixinMethod.isStatic() != deobSourceMethod.isStatic())
				{
					throw new Injexception("Mixin method " + mixinMethod + " should be " + (deobSourceMethod.isStatic() ? "static" : "non-static"));
				}

				// The actual method we're copying, including code etc
				Method sourceMethod = inject.toVanilla(deobSourceMethod);

				if (mixinMethod.getDescriptor().size() > sourceMethod.getDescriptor().size())
				{
					throw new Injexception("Mixin methods cannot have more parameters than their corresponding ob method");
				}

				Method copy = new Method(targetClass, "copy$" + copiedName, sourceMethod.getObfuscatedSignature());
				moveCode(copy, sourceMethod.getCode());
				copy.setAccessFlags(sourceMethod.getAccessFlags());
				copy.setPublic();
				copy.getExceptions().getExceptions().addAll(sourceMethod.getExceptions().getExceptions());
				copy.getAnnotations().getAnnotations().addAll(sourceMethod.getAnnotations().getAnnotations());
				targetClass.addMethod(copy);
				++copied;

				/*
				 * If the desc for the mixin method and the desc for the ob method
				 * are the same in length, assume that the mixin method is taking
				 * care of the garbage parameter itself.
				*/
				boolean hasGarbageValue = mixinMethod.getDescriptor().size() != sourceMethod.getDescriptor().size()
					&& deobSourceMethod.getDescriptor().size() < copy.getDescriptor().size();

				copiedMethods.put(mixinMethod.getPoolMethod(), new CopiedMethod(copy, hasGarbageValue));
			}

			// Handle the rest of the mixin types
			for (Method mixinMethod : mixinClass.getMethods())
			{
				boolean isClinit = "<clinit>".equals(mixinMethod.getName());
				boolean isInit = "<init>".equals(mixinMethod.getName());
				boolean hasInject = mixinMethod.getAnnotations().find(INJECT) != null;

				// You can't annotate clinit, so its always injected
				if ((hasInject && isInit) || isClinit)
				{
					if (!"()V".equals(mixinMethod.getDescriptor().toString()))
					{
						throw new Injexception("Injected constructors cannot have arguments");
					}

					Method[] originalMethods = targetClass.getMethods().stream()
						.filter(m -> m.getName().equals(mixinMethod.getName()))
						.toArray(Method[]::new);

					String name = mixinMethod.getName();

					// If there isn't a <clinit> already just inject ours, otherwise rename it
					// This is always true for <init>
					if (originalMethods.length > 0)
					{
						name = "rl$$" + (isInit ? "init" : "clinit");
					}

					String numberlessName = name;
					for (int i = 1; targetClass.findMethod(name, mixinMethod.getDescriptor()) != null; i++)
					{
						name = numberlessName + i;
					}

					Method copy = new Method(targetClass, name, mixinMethod.getDescriptor());
					moveCode(copy, mixinMethod.getCode());
					copy.setAccessFlags(mixinMethod.getAccessFlags());
					copy.setPrivate();
					assert mixinMethod.getExceptions().getExceptions().isEmpty();

					// Remove the call to the superclass's ctor
					if (isInit)
					{
						Instructions instructions = copy.getCode().getInstructions();
						ListIterator<Instruction> listIter = instructions.listIterator();
						for (; listIter.hasNext(); )
						{
							Instruction instr = listIter.next();
							if (instr instanceof InvokeSpecial)
							{
								InvokeSpecial invoke = (InvokeSpecial) instr;
								assert invoke.getMethod().getName().equals("<init>");
								listIter.remove();
								int pops = invoke.getMethod().getType().getArguments().size() + 1;
								for (int i = 0; i < pops; i++)
								{
									listIter.add(new Pop(instructions));
								}
								break;
							}
						}
					}

					setOwnersToTargetClass(mixinClass, targetClass, copy, copiedMethods);
					targetClass.addMethod(copy);

					// Call our method at the return point of the matching method(s)
					for (Method om : originalMethods)
					{
						Instructions instructions = om.getCode().getInstructions();
						ListIterator<Instruction> listIter = instructions.listIterator();

						while (listIter.hasNext())
						{
							Instruction instr = listIter.next();
							if (instr instanceof ReturnInstruction)
							{
								listIter.previous();
								if (isInit)
								{
									listIter.add(new ALoad(instructions, 0));
									listIter.add(new InvokeSpecial(instructions, copy.getPoolMethod()));
								}
								else if (isClinit)
								{
									listIter.add(new InvokeStatic(instructions, copy.getPoolMethod()));
								}
								listIter.next();
							}
						}
					}

					log.debug("Injected mixin method {} to {}", copy, targetClass);
					++injected;
				}
				else if (hasInject)
				{
					// Make sure the method doesn't invoke copied methods
					for (Instruction i : mixinMethod.getCode().getInstructions())
					{
						if (i instanceof InvokeInstruction)
						{
							InvokeInstruction ii = (InvokeInstruction) i;

							if (copiedMethods.containsKey(ii.getMethod()))
							{
								throw new Injexception("Injected methods cannot invoke copied methods");
							}
						}
					}

					Method copy = new Method(targetClass, mixinMethod.getName(), mixinMethod.getDescriptor());
					moveCode(copy, mixinMethod.getCode());
					copy.setAccessFlags(mixinMethod.getAccessFlags());
					copy.setPublic();
					assert mixinMethod.getExceptions().getExceptions().isEmpty();

					setOwnersToTargetClass(mixinClass, targetClass, copy, copiedMethods);

					targetClass.addMethod(copy);

					log.debug("Injected mixin method {} to {}", copy, targetClass);
					++injected;
				}
				else if (mixinMethod.getAnnotations().find(REPLACE) != null)
				{
					Annotation replaceAnnotation = mixinMethod.getAnnotations().find(REPLACE);
					String replacedName = (String) replaceAnnotation.getElement().getValue();

					ClassFile deobClass = inject.toDeob(targetClass.getName());
					Method deobMethod = findDeobMatching(deobClass, mixinMethod, replacedName);

					if (deobMethod == null)
					{
						throw new Injexception("Failed to find the deob method " + replacedName + " for mixin " + mixinClass);
					}

					if (mixinMethod.isStatic() != deobMethod.isStatic())
					{
						throw new Injexception("Mixin method " + mixinMethod + " should be "
							+ (deobMethod.isStatic() ? "static" : "non-static"));
					}

					String obReplacedName = InjectUtil.getObfuscatedName(deobMethod);
					Signature obMethodSignature = deobMethod.getObfuscatedSignature();

					// Find the vanilla class where the method to copy is in
					ClassFile obCf = inject.toVanilla(deobMethod.getClassFile());

					Method obMethod = obCf.findMethod(obReplacedName, obMethodSignature);
					assert obMethod != null : "obfuscated method " + obReplacedName + obMethodSignature + " does not exist";

					if (mixinMethod.getDescriptor().size() > obMethod.getDescriptor().size())
					{
						throw new Injexception("Mixin methods cannot have more parameters than their corresponding ob method");
					}

					Type returnType = mixinMethod.getDescriptor().getReturnValue();
					Type deobReturnType = InjectUtil.apiToDeob(inject, returnType);
					if (!returnType.equals(deobReturnType))
					{
						ClassFile deobReturnTypeClassFile = inject.getDeobfuscated()
							.findClass(deobReturnType.getInternalName());
						if (deobReturnTypeClassFile != null)
						{
							ClassFile obReturnTypeClass = inject.toVanilla(deobReturnTypeClassFile);

							Instructions instructions = mixinMethod.getCode().getInstructions();
							ListIterator<Instruction> listIter = instructions.listIterator();
							while (listIter.hasNext())
							{
								Instruction instr = listIter.next();
								if (instr instanceof ReturnInstruction)
								{
									listIter.previous();
									CheckCast checkCast = new CheckCast(instructions);
									checkCast.setType(new Type(obReturnTypeClass.getName()));
									listIter.add(checkCast);
									listIter.next();
								}
							}
						}
					}

					moveCode(obMethod, mixinMethod.getCode());

					boolean hasGarbageValue = mixinMethod.getDescriptor().size() != obMethod.getDescriptor().size()
						&& deobMethod.getDescriptor().size() < obMethodSignature.size();

					if (hasGarbageValue)
					{
						int garbageIndex = obMethod.isStatic()
							? obMethod.getDescriptor().size() - 1
							: obMethod.getDescriptor().size();

					/*
						If the mixin method doesn't have the garbage parameter,
						the compiler will have produced code that uses the garbage
						parameter's local variable index for other things,
						so we'll have to add 1 to all loads/stores to indices
						that are >= garbageIndex.
					 */
						shiftLocalIndices(obMethod.getCode().getInstructions(), garbageIndex);
					}

					setOwnersToTargetClass(mixinClass, targetClass, obMethod, copiedMethods);

					log.debug("Replaced method {} with mixin method {}", obMethod, mixinMethod);
					replaced++;
				}
			}
		}
	}

	private void moveCode(Method targetMethod, Code sourceCode)
	{
		Code newCode = new Code(targetMethod);
		newCode.setMaxStack(sourceCode.getMaxStack());
		newCode.getInstructions().getInstructions().addAll(sourceCode.getInstructions().getInstructions());
		// Update instructions for each instruction
		for (Instruction i : newCode.getInstructions())
		{
			i.setInstructions(newCode.getInstructions());
		}
		newCode.getExceptions().getExceptions().addAll(sourceCode.getExceptions().getExceptions());
		for (net.runelite.asm.attributes.code.Exception e : newCode.getExceptions().getExceptions())
		{
			e.setExceptions(newCode.getExceptions());
		}
		targetMethod.setCode(newCode);
	}

	private void setOwnersToTargetClass(ClassFile mixinCf, ClassFile cf, Method method, Map<net.runelite.asm.pool.Method, CopiedMethod> copiedMethods) throws Injexception
	{
		ListIterator<Instruction> iterator = method.getCode().getInstructions().listIterator();

		while (iterator.hasNext())
		{
			Instruction i = iterator.next();

			if (i instanceof ANewArray)
			{
				Type type = ((ANewArray) i).getType_();
				ClassFile deobTypeClass = inject.toDeob(type.getInternalName());

				if (deobTypeClass != null)
				{
					Type newType = new Type("L" + inject.toVanilla(deobTypeClass).getName() + ";");

					((ANewArray) i).setType(newType);
					log.debug("Replaced {} type {} with type {}", i, type, newType);
				}
			}
			else if (i instanceof InvokeInstruction)
			{
				InvokeInstruction ii = (InvokeInstruction) i;

				CopiedMethod copiedMethod = copiedMethods.get(ii.getMethod());
				if (copiedMethod != null)
				{
					ii.setMethod(copiedMethod.obMethod.getPoolMethod());

					// Pass through garbage value if the method has one
					if (copiedMethod.hasGarbageValue)
					{
						int garbageIndex = copiedMethod.obMethod.isStatic()
							? copiedMethod.obMethod.getDescriptor().size() - 1
							: copiedMethod.obMethod.getDescriptor().size();

						iterator.previous();
						iterator.add(new ILoad(method.getCode().getInstructions(), garbageIndex));
						iterator.next();
					}
				}
				else if (ii.getMethod().getClazz().getName().equals(mixinCf.getName()))
				{
					ii.setMethod(new net.runelite.asm.pool.Method(
						new net.runelite.asm.pool.Class(cf.getName()),
						ii.getMethod().getName(),
						ii.getMethod().getType()
					));
				}
			}
			else if (i instanceof FieldInstruction)
			{
				FieldInstruction fi = (FieldInstruction) i;

				Field shadowed = shadowFields.get(fi.getField());
				if (shadowed != null)
				{
					fi.setField(shadowed.getPoolField());
				}
				else if (fi.getField().getClazz().getName().equals(mixinCf.getName()))
				{
					fi.setField(new net.runelite.asm.pool.Field(
						new net.runelite.asm.pool.Class(cf.getName()),
						fi.getField().getName(),
						fi.getField().getType()
					));
				}
			}
			else if (i instanceof PushConstantInstruction)
			{
				PushConstantInstruction pi = (PushConstantInstruction) i;
				if (mixinCf.getPoolClass().equals(pi.getConstant()))
				{
					pi.setConstant(cf.getPoolClass());
				}
			}

			verify(mixinCf, i);
		}
	}

	private void verify(ClassFile mixinCf, Instruction i) throws Injexception
	{
		if (i instanceof FieldInstruction)
		{
			FieldInstruction fi = (FieldInstruction) i;

			if (fi.getField().getClazz().getName().equals(mixinCf.getName()))
			{
				if (i instanceof PutField || i instanceof GetField)
				{
					throw new Injexception("Access to non static member field of mixin");
				}

				Field field = fi.getMyField();
				if (field != null && !field.isPublic())
				{
					throw new Injexception("Static access to non public field " + field);
				}
			}
		}
		else if (i instanceof InvokeStatic)
		{
			InvokeStatic is = (InvokeStatic) i;

			if (is.getMethod().getClazz() != mixinCf.getPoolClass()
				&& is.getMethod().getClazz().getName().startsWith(MIXIN_BASE))
			{
				throw new Injexception("Invoking static methods of other mixins is not supported");
			}
		}
		else if (i instanceof InvokeDynamic)
		{
			// RS classes don't verify under java 7+ due to the
			// super() invokespecial being inside of a try{}
			throw new Injexception("Injected bytecode must be Java 6 compatible");
		}
	}

	private Method findDeobMatching(ClassFile deobClass, Method mixinMethod, String deobName) throws Injexception
	{
		List<Method> matching = new ArrayList<>();

		for (Method method : deobClass.getMethods())
		{
			if (!deobName.equals(method.getName()))
			{
				continue;
			}

			if (InjectUtil.apiToDeobSigEquals(inject, method.getDescriptor(), mixinMethod.getDescriptor()))
			{
				matching.add(method);
			}
		}

		if (matching.size() > 1)
		{
			// this happens when it has found several deob methods for some mixin method,
			// to get rid of the error, refine your search by making your mixin method have more parameters
			throw new Injexception("There are several matching methods when there should only be one");
		}
		else if (matching.size() == 1)
		{
			return matching.get(0);
		}

		return inject.getDeobfuscated().findStaticMethod(deobName);
	}

	private void shiftLocalIndices(Instructions instructions, int startIdx)
	{
		for (Instruction i : instructions)
		{
			if (i instanceof LVTInstruction)
			{
				LVTInstruction lvti = (LVTInstruction) i;

				if (lvti.getVariableIndex() >= startIdx)
				{
					lvti.setVariableIndex(lvti.getVariableIndex() + 1);
				}
			}
		}
	}

	@AllArgsConstructor
	private static class CopiedMethod
	{
		private Method obMethod;
		private boolean hasGarbageValue;
	}
}
