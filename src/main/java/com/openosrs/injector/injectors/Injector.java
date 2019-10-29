package com.openosrs.injector.injectors;

import com.openosrs.injector.Injexception;
import net.runelite.asm.Named;

public interface Injector extends Named
{
	/**
	 * Where all the injection should be done
	 */
	void inject() throws Injexception;

	/**
	 * Get a name the injector is going to be referred to in logging
	 */
	default String getName()
	{
		return this.getClass().getSimpleName();
	}

	/**
	 * Called before inject, AbstractInjector currently uses it to start a stopwatch
	 */
	void start();

	/**
	 * Gets a message logged at quiet level when the injector ends
	 */
	String getCompletionMsg();
}
