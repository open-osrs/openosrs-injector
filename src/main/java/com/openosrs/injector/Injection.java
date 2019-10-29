package com.openosrs.injector;

import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.injection.InjectTaskHandler;
import com.openosrs.injector.injectors.InjectConstruct;
import com.openosrs.injector.injectors.Injector;
import com.openosrs.injector.injectors.InterfaceInjector;
import com.openosrs.injector.injectors.MixinInjector;
import com.openosrs.injector.injectors.RSApiInjector;
import com.openosrs.injector.injectors.raw.ClearColorBuffer;
import com.openosrs.injector.injectors.raw.DrawAfterWidgets;
import com.openosrs.injector.injectors.raw.Occluder;
import com.openosrs.injector.injectors.raw.RasterizerHook;
import com.openosrs.injector.injectors.raw.RenderDraw;
import com.openosrs.injector.injectors.raw.ScriptVM;
import com.openosrs.injector.rsapi.RSApi;
import java.io.File;
import java.io.IOException;
import net.runelite.deob.util.JarUtil;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class Injection extends InjectData implements InjectTaskHandler
{
	private static final Logger log = Logging.getLogger(Injection.class);

	public Injection(File vanilla, FileTree rsclient, FileTree rsapi, FileTree mixins) throws Injexception, IOException
	{
		super(
			JarUtil.loadJar(vanilla),
			JarUtil.loadClasses(rsclient.getFiles()),
			JarUtil.loadClasses(mixins.getFiles()),
			new RSApi(rsapi)
		);
	}

	public void inject() throws Injexception
	{
		log.debug("Starting injection");

		inject(new InterfaceInjector(this));

		inject(new RasterizerHook(this));

		inject(new MixinInjector(this));

		// This is where field hooks runs

		// This is where method hooks runs

		inject(new InjectConstruct(this));

		inject(new RSApiInjector(this));

		inject(new DrawAfterWidgets(this));

		inject(new ScriptVM(this));

		// All GPU raw injectors should probably be combined, especially RenderDraw and Occluder
		inject(new ClearColorBuffer(this));

		inject(new RenderDraw(this));

		inject(new Occluder(this));

		// inject(new DrawMenu(this));

		// inject(new HidePlayerAttacks(this));

		new InjectorValidator(this).validate();
	}

	public void save(File outputJar) throws IOException
	{
		log.info("Saving jar to {}", outputJar.toString());

		JarUtil.saveJar(this.getVanilla(), outputJar);
	}

	private void inject(Injector injector) throws Injexception
	{
		final String name = injector.getName();

		log.info("Starting {}", name);

		injector.start();

		injector.inject();

		log.lifecycle("{} {}", name, injector.getCompletionMsg());

		if (injector instanceof Validator)
			validate((Validator) injector);
	}

	private void validate(Validator validator) throws Injexception
	{
		final String name = validator.getName();

		if (!validator.validate())
		{
			throw new Injexception(name + " failed validation");
		}
	}

	public void runChildInjector(Injector injector) throws Injexception
	{
		inject(injector);
	}
}
