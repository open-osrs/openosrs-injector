/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this source tree.
 */
package com.openosrs.injector.rsapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.Data;
import net.runelite.asm.attributes.Annotations;
import net.runelite.asm.attributes.annotation.Annotation;
import net.runelite.asm.pool.Class;
import net.runelite.asm.pool.Method;
import net.runelite.asm.signature.Signature;
import org.jetbrains.annotations.NotNull;
import static com.openosrs.injector.rsapi.RSApi.CONSTRUCT;
import static com.openosrs.injector.rsapi.RSApi.IMPORT;

@Data
public class RSApiClass implements Iterable<RSApiMethod>
{
	private Class clazz;
	private final List<Class> interfaces = new ArrayList<>();
	private final List<RSApiMethod> methods = new ArrayList<>();
	private final List<RSApiClass> apiInterfaces = new ArrayList<>();

	private final Map<String, List<RSApiMethod>> imports = new HashMap<>();

	void init(List<RSApiMethod> constructList)
	{
		for (RSApiMethod method : this)
		{
			if (method.isSynthetic())
				continue;

			final Annotations annotations = method.getAnnotations();
			if (annotations.find(CONSTRUCT) != null)
			{
				constructList.add(method);
				continue;
			}

			final Annotation imported = annotations.find(IMPORT);
			if (imported != null)
			{
				final String importStr = imported.getElement().getString();

				imports.computeIfAbsent(
					importStr,
					(str) -> new ArrayList<>()
				).add(method);
			}
		}
	}

	RSApiMethod addMethod(String name, Signature sig, int access)
	{
		final RSApiMethod method = new RSApiMethod(new Method(clazz, name, sig), access);
		methods.add(method);
		return method;
	}

	public String getName()
	{
		return clazz.getName();
	}

	public void fetchImported(List<RSApiMethod> to, String str)
	{
		List<RSApiMethod> imported = imports.get(str);
		if (imported == null)
			return;

		to.addAll(imported);
	}

	@NotNull
	public Iterator<RSApiMethod> iterator()
	{
		return this.methods.iterator();
	}
}
