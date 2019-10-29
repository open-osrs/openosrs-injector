package com.openosrs.injector;

import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.injectors.Injector;
import com.openosrs.injector.rsapi.RSApi;
import net.runelite.asm.ClassGroup;

public class TestInjection extends InjectData
{
	public TestInjection(ClassGroup vanilla, ClassGroup deobfuscated, ClassGroup mixins, RSApi rsApi)
	{
		super(vanilla, deobfuscated, mixins, rsApi);
	}

	@Override
	public void runChildInjector(Injector injector) throws Injexception
	{
		injector.inject();
	}
}
