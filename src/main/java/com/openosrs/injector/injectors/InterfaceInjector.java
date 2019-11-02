package com.openosrs.injector.injectors;

import com.openosrs.injector.injection.InjectData;
import static com.openosrs.injector.rsapi.RSApi.API_BASE;
import net.runelite.asm.ClassFile;
import net.runelite.asm.Interfaces;
import net.runelite.asm.pool.Class;
import net.runelite.deob.DeobAnnotations;

public class InterfaceInjector extends AbstractInjector
{
	private int implemented = 0;

	public InterfaceInjector(InjectData inject)
	{
		super(inject);
	}

	public void inject()
	{
		// forEachPair performs actions on a deob-vanilla pair, which is what's needed here
		inject.forEachPair(this::injectInterface);

		log.info("Injected {} interfaces", implemented);
	}

	private void injectInterface(final ClassFile deobCf, final ClassFile vanillaCf)
	{
		final String impls = DeobAnnotations.getImplements(deobCf);

		if (impls == null)
			return;

		final String fullName = API_BASE + impls;
		if (!inject.getRsApi().hasClass(fullName))
		{
			log.debug("Class {} implements nonexistent interface {}, skipping interface injection",
				deobCf.getName(),
				fullName
			);

			return;
		}

		final Interfaces interfaces = vanillaCf.getInterfaces();
		interfaces.addInterface(new Class(fullName));
		implemented++;

		inject.addToDeob(fullName, deobCf);
	}
}
