/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this source tree.
 */
package com.openosrs.injector;

import com.google.common.hash.Hashing;
import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.injection.InjectTaskHandler;
import com.openosrs.injector.injectors.CreateAnnotations;
import com.openosrs.injector.injectors.InjectConstruct;
import com.openosrs.injector.injectors.Injector;
import com.openosrs.injector.injectors.InterfaceInjector;
import com.openosrs.injector.injectors.MixinInjector;
import com.openosrs.injector.injectors.RSApiInjector;
import com.openosrs.injector.injectors.raw.AddPlayerToMenu;
import com.openosrs.injector.injectors.raw.ClearColorBuffer;
import com.openosrs.injector.injectors.raw.CopyRuneLiteClasses;
import com.openosrs.injector.injectors.raw.DrawMenu;
import com.openosrs.injector.injectors.raw.GameDrawingMode;
import com.openosrs.injector.injectors.raw.GraphicsObject;
import com.openosrs.injector.injectors.raw.Occluder;
import com.openosrs.injector.injectors.raw.RasterizerAlpha;
import com.openosrs.injector.injectors.raw.RenderDraw;
import com.openosrs.injector.injectors.raw.RuneLiteIterables;
import com.openosrs.injector.injectors.raw.RuneliteMenuEntry;
import com.openosrs.injector.injectors.raw.RuneliteObject;
import com.openosrs.injector.injectors.raw.ScriptVM;
import com.openosrs.injector.rsapi.RSApi;
import com.openosrs.injector.transformers.InjectTransformer;
import com.openosrs.injector.transformers.Java8Ifier;
import com.openosrs.injector.transformers.SourceChanger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.runelite.deob.util.JarUtil;
import static net.runelite.deob.util.JarUtil.load;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class Injection extends InjectData implements InjectTaskHandler
{
	private static final Logger log = Logging.getLogger(Injection.class);
	public static boolean development = true;
	public static String skips = "";

	public Injection(File vanilla, File rsclient, File mixins, FileTree rsapi, boolean development, String skip)
	{
		super(
			load(vanilla),
			load(rsclient),
			load(mixins),
			new RSApi(rsapi)
		);

		Injection.development = development;
		Injection.skips = skip;
	}

	public void inject()
	{
		log.debug("[DEBUG] Starting injection");

		transform(new Java8Ifier(this));

		inject(new CreateAnnotations(this));

		inject(new GraphicsObject(this));

		inject(new CopyRuneLiteClasses(this));

		inject(new RuneLiteIterables(this));

		inject(new RuneliteObject(this));

		inject(new InterfaceInjector(this));

		inject(new RasterizerAlpha(this));

		inject(new MixinInjector(this));

		// This is where field hooks runs

		// This is where method hooks runs

		inject(new InjectConstruct(this));

		inject(new RSApiInjector(this));

		//inject(new DrawAfterWidgets(this));

		inject(new ScriptVM(this));

		// All GPU raw injectors should probably be combined, especially RenderDraw and Occluder
		inject(new ClearColorBuffer(this));

		inject(new RenderDraw(this));

		inject(new Occluder(this));

		inject(new DrawMenu(this));

		inject(new GameDrawingMode(this));

		inject(new AddPlayerToMenu(this));

		inject(new RuneliteMenuEntry(this));

		validate(new InjectorValidator(this));

		transform(new SourceChanger(this));
	}

	public void save(File outputJar)
	{
		log.info("[INFO] Saving jar to {}", outputJar.toString());

		JarUtil.save(this.getVanilla(), outputJar);
	}

	public void hash(File output, File vanilla)
	{
		log.info("[INFO] Saving hash to {}", output.toString());

		try
		{
			String hash = com.google.common.io.Files.asByteSource(vanilla).hash(Hashing.sha256()).toString();
			log.lifecycle("Writing vanilla hash: {}", hash);
			Files.write(output.toPath(), hash.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex)
		{
			log.lifecycle("Failed to write vanilla hash file");
			throw new RuntimeException(ex);
		}
	}

	private void inject(Injector injector)
	{
		final String name = injector.getName();

		if (injector.shouldRun())
		{
			injector.start();

			injector.inject();

			String completionMsg = injector.getCompletionMsg();

			if (completionMsg != null)
			{
				log.lifecycle("{} {}", name, completionMsg);
			}
		}

		if (injector instanceof Validator)
		{
			validate((Validator) injector);
		}
	}

	private void validate(Validator validator)
	{
		final String name = validator.getName();

		if (!validator.validate())
		{
			throw new InjectException(name + " failed validation");
		}
	}

	private void transform(InjectTransformer transformer)
	{
		final String name = transformer.getName();

		transformer.transform();

		log.lifecycle("{} {}", name, transformer.getCompletionMsg());
	}

	public void runChildInjector(Injector injector)
	{
		inject(injector);
	}
}
