package com.openosrs.injector.injectors.raw;

import com.google.common.io.ByteStreams;
import com.openosrs.injector.TestInjection;
import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.rsapi.RSApi;
import net.runelite.asm.ClassFile;
import net.runelite.asm.ClassGroup;
import net.runelite.deob.util.JarUtil;
import org.junit.Test;

public class DrawAfterWidgetsTest
{
	@Test
	public void testInjectDrawWidgetsRev160() throws Exception
	{
		// Rev 160 does not have the drawWidgets call inlined

		ClassFile deobClient = JarUtil.loadClass(ByteStreams.toByteArray(getClass().getResourceAsStream("/drawafterwidgets/Client_deob160.class")));
		ClassFile deobRasterizer = JarUtil.loadClass(ByteStreams.toByteArray(getClass().getResourceAsStream("/drawafterwidgets/Rasterizer2D_deob160.class")));

		ClassGroup deob = new ClassGroup();
		deob.addClass(deobClient);
		deob.addClass(deobRasterizer);

		ClassFile obClient = JarUtil.loadClass(ByteStreams.toByteArray(getClass().getResourceAsStream("/drawafterwidgets/Client_ob160.class")));
		ClassFile obRasterizer = JarUtil.loadClass(ByteStreams.toByteArray(getClass().getResourceAsStream("/drawafterwidgets/Rasterizer2D_ob160.class")));

		ClassGroup vanilla = new ClassGroup();
		vanilla.addClass(obClient);
		vanilla.addClass(obRasterizer);

		InjectData inject = new TestInjection(vanilla, deob, new ClassGroup(), new RSApi());
		new DrawAfterWidgets(inject).inject();
	}

	@Test
	public void testInjectDrawWidgetsRev180() throws Exception
	{
		// Rev 180 has the drawWidgets call inlined

		ClassFile deobClient = JarUtil.loadClass(ByteStreams.toByteArray(getClass().getResourceAsStream("/drawafterwidgets/Client_deob180.class")));
		ClassFile deobRasterizer = JarUtil.loadClass(ByteStreams.toByteArray(getClass().getResourceAsStream("/drawafterwidgets/Rasterizer2D_deob180.class")));

		ClassGroup deob = new ClassGroup();
		deob.addClass(deobClient);
		deob.addClass(deobRasterizer);

		ClassFile obClient = JarUtil.loadClass(ByteStreams.toByteArray(getClass().getResourceAsStream("/drawafterwidgets/Client_ob180.class")));
		ClassFile obRasterizer = JarUtil.loadClass(ByteStreams.toByteArray(getClass().getResourceAsStream("/drawafterwidgets/Rasterizer2D_ob180.class")));

		ClassGroup vanilla = new ClassGroup();
		vanilla.addClass(obClient);
		vanilla.addClass(obRasterizer);

		InjectData inject = new TestInjection(vanilla, deob, new ClassGroup(), new RSApi());
		new DrawAfterWidgets(inject).inject();
	}
}
