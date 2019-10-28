package com.openosrs.injector.injectors;

import com.google.common.base.Stopwatch;
import com.openosrs.injector.injection.InjectData;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public abstract class AbstractInjector implements Injector
{
	protected final InjectData inject;
	protected final Logger log = Logging.getLogger(this.getClass());
	private Stopwatch stopwatch;

	protected AbstractInjector(InjectData inject)
	{
		this.inject = inject;
	}

	public void start()
	{
		stopwatch = Stopwatch.createStarted();
	}

	public final String getCompletionMsg()
	{
		return "finished in " + stopwatch.toString();
	}
}
