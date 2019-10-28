package com.openosrs.injector.injectors;

import com.openosrs.injector.InjectUtil;
import com.openosrs.injector.Injexception;
import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.injectors.rsapi.InjectGetter;
import com.openosrs.injector.injectors.rsapi.InjectInvoke;
import com.openosrs.injector.injectors.rsapi.InjectSetter;
import static com.openosrs.injector.rsapi.RSApi.*;
import com.openosrs.injector.rsapi.RSApiClass;
import com.openosrs.injector.rsapi.RSApiMethod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Field;
import net.runelite.asm.Method;
import net.runelite.asm.Type;
import net.runelite.asm.signature.Signature;
import net.runelite.deob.DeobAnnotations;
import net.runelite.deob.deobfuscators.arithmetic.DMath;

public class RSApiInjector extends AbstractInjector
{
	private final Map<Field, List<RSApiMethod>> retryFields = new HashMap<>();
	private int get = 0, set = 0, voke = 0;

	public RSApiInjector(InjectData inject)
	{
		super(inject);
	}

	public void inject() throws Injexception
	{
		for (final ClassFile deobClass : inject.getDeobfuscated())
		{
			final RSApiClass implementingClass = inject.getRsApi().findClass(API_BASE + deobClass.getName());

			injectFields(deobClass, implementingClass);
			injectMethods(deobClass, implementingClass); // aka invokers
		}

		retryFailures();

		log.info("Injected {} getters, {} setters, and {} invokers", get, set, voke);
	}

	private void injectFields(ClassFile deobClass, RSApiClass implementingClass) throws Injexception
	{
		for (Field deobField : deobClass.getFields())
		{
			final String exportedName = InjectUtil.getExportedName(deobField);
			if (exportedName == null)
			{
				continue;
			}

			final List<RSApiMethod> matching = new ArrayList<>();

			if (deobField.isStatic())
			{
				for (RSApiClass api : inject.getRsApi())
				{
					api.fetchImported(matching, exportedName);
				}
			}
			else if (implementingClass != null)
			{
				implementingClass.fetchImported(matching, exportedName);
			}

			if (matching.size() == 0)
			{
				continue;
			}

			final Type deobType = deobField.getType();

			// We're dealing with a field here, so only getter/setter methods match
			ListIterator<RSApiMethod> it = matching.listIterator();
			while (it.hasNext())
			{
				RSApiMethod apiMethod = it.next();

				if (apiMethod.isInjected())
				{
					it.remove();
					continue;
				}

				final Signature sig = apiMethod.getSignature();

				if (sig.isVoid())
				{
					if (sig.size() == 1)
					{
						Type type = InjectUtil.apiToDeob(inject, sig.getTypeOfArg(0));
						if (deobType.equals(type))
						{
							continue;
						}
					}
				}
				else if (sig.size() == 0)
				{
					Type type = InjectUtil.apiToDeob(inject, sig.getReturnValue());
					if (deobType.equals(type))
					{
						continue;
					}
				}

				it.remove();
			}

			if (matching.size() == 0)
			{
				continue;
			}
			else if (matching.size() > 2)
			{
				retryFields.put(deobField, new ArrayList<>(matching));
				continue;
			}

			final Field vanillaField = inject.toVanilla(deobField);
			final Number getter = DeobAnnotations.getObfuscatedGetter(deobField);

			if (deobField.isStatic() != vanillaField.isStatic()) // Can this even happen
			{
				throw new Injexception("Something went horribly wrong, and this should honestly never happen, but you never know. Btw it's the static-ness");
			}

			for (RSApiMethod apiMethod : matching)
			{
				final ClassFile targetClass = InjectUtil.fromApiMethod(inject, apiMethod);
				apiMethod.setInjected(true);

				if (apiMethod.getSignature().isVoid())
				{
					++set;
					log.debug("Injecting setter {} for {} into {}", apiMethod.getMethod(), vanillaField.getPoolField(), targetClass.getPoolClass());
					InjectSetter.inject(
						targetClass,
						apiMethod,
						vanillaField,
						modInverseOrNull(getter)
					);
				}
				else
				{
					++get;
					log.debug("Injecting getter {} for {} into {}", apiMethod.getMethod(), vanillaField.getPoolField(), targetClass.getPoolClass());
					InjectGetter.inject(
						targetClass,
						apiMethod,
						vanillaField,
						getter
					);
				}
			}
		}
	}

	private void injectMethods(ClassFile deobClass, RSApiClass implementingClass) throws Injexception
	{
		for (Method deobMethod : deobClass.getMethods())
		{
			final String exportedName = InjectUtil.getExportedName(deobMethod);
			if (exportedName == null)
			{
				continue;
			}

			final List<RSApiMethod> matching = new ArrayList<>();

			if (deobMethod.isStatic())
			{
				for (RSApiClass api : inject.getRsApi())
				{
					api.fetchImported(matching, exportedName);
				}
			}
			else if (implementingClass != null)
			{
				implementingClass.fetchImported(matching, exportedName);
			}

			if (matching.size() == 0)
			{
				continue;
			}

			final Signature deobSig = deobMethod.getDescriptor();

			ListIterator<RSApiMethod> it = matching.listIterator();
			while (it.hasNext())
			{
				final RSApiMethod apiMethod = it.next();
				final Signature apiSig = apiMethod.getSignature();

				if (apiMethod.isInjected()
					|| !InjectUtil.apiToDeobSigEquals(inject, deobSig, apiSig))
				{
					it.remove();
				}
			}

			if (matching.size() == 1)
			{
				final RSApiMethod apiMethod = matching.get(0);

				final ClassFile targetClass = InjectUtil.fromApiMethod(inject, apiMethod);
				final Method vanillaMethod = inject.toVanilla(deobMethod);
				final String garbage = DeobAnnotations.getDecoder(deobMethod);
				log.debug("Injecting invoker {} for {} into {}", apiMethod.getMethod(), vanillaMethod.getPoolMethod(), targetClass.getPoolClass());
				InjectInvoke.inject(targetClass, apiMethod, vanillaMethod, garbage);
				++voke;
				apiMethod.setInjected(true);
			}
			else if (matching.size() != 0)
			{
				throw new Injexception("Multiple api imports matching method " + deobMethod.getPoolMethod());
			}
		}
	}

	private void retryFailures() throws Injexception
	{
		for (Map.Entry<Field, List<RSApiMethod>> entry : retryFields.entrySet())
		{
			final List<RSApiMethod> matched = entry.getValue();
			final Field field = entry.getKey();

			matched.removeIf(RSApiMethod::isInjected);

			if (matched.size() > 2)
			{
				throw new Injexception("More than 2 imported api methods for field " + field.getPoolField());
			}

			final Field vanillaField = inject.toVanilla(field);
			final Number getter = DeobAnnotations.getObfuscatedGetter(field);

			for (RSApiMethod apiMethod : matched)
			{
				final ClassFile targetClass = InjectUtil.fromApiMethod(inject, apiMethod);

				apiMethod.setInjected(true);

				if (apiMethod.getSignature().isVoid())
				{
					++set;
					log.debug("Injecting setter {} for {} into {}", apiMethod.getMethod(), field.getPoolField(), targetClass.getPoolClass());
					InjectSetter.inject(
						targetClass,
						apiMethod,
						vanillaField,
						modInverseOrNull(getter)
					);
				}
				else
				{
					++get;
					log.debug("Injecting getter {} for {} into {}", apiMethod.getMethod(), field.getPoolField(), targetClass.getPoolClass());
					InjectGetter.inject(
						targetClass,
						apiMethod,
						vanillaField,
						getter
					);
				}
			}
		}
	}

	private static Number modInverseOrNull(Number getter)
	{
		if (getter == null)
		{
			return null;
		}

		// inverse getter to get the setter
		return DMath.modInverse(getter);
	}
}
