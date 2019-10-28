package com.openosrs.injector.injectors;

import com.openosrs.injector.Injexception;

public interface Injector
{
	/**
	 * Where all the injection should be done
	 */
	void inject() throws Injexception;

	/**
	 * Should return `true` if injection was succesful, `false` otherwise.
	 */
	default boolean validate()
	{
		return true;
	}

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
