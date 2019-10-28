package com.openosrs.injector.injection;

import com.openosrs.injector.Injexception;
import java.io.File;
import java.io.IOException;

/**
 * Interface containing all the methods gradle needs to know about
 */
public interface InjectTaskHandler
{
	/**
	 * The actual method that does all the work
	 */
	void inject() throws Injexception;

	/**
	 * Call this to save the injected jar to outputJar
	 */
	void save(File outputJar) throws IOException;
}
