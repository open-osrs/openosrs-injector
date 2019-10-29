package com.openosrs.injector;

import net.runelite.asm.Named;

public interface Validator extends Named
{
	boolean validate();

	default String getName()
	{
		return this.getClass().getSimpleName();
	}
}
