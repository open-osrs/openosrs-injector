import com.openosrs.injector.rsapi.RSApi;
import com.openosrs.injector.rsapi.RSApiClass;
import com.openosrs.injector.rsapi.RSApiClassVisitor;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.objectweb.asm.ClassReader;

public class RSApiTest
{
	private final RSApi api = new RSApi();

	@Test
	@Ignore
	public void test() throws IOException
	{
		loadAndAdd("/net/runelite/rs/api/RSTest.class");
		loadAndAdd("/net/runelite/rs/api/RSInterface.class");
		api.init();

		List<RSApiClass> classes = api.getClasses();
		assert classes.size() == 2;
		RSApiClass clazz = api.findClass("net/runelite/rs/api/RSTest");
		assert clazz != null;
		assert clazz.getMethods().size() == 4;
	}

	private void loadAndAdd(String path) throws IOException
	{
		List<RSApiClass> classes = api.getClasses();

		try (InputStream is = RSApiTest.class.getResourceAsStream(path))
		{
			ClassReader reader = new ClassReader(is);
			RSApiClass apiClass = new RSApiClass();

			reader.accept(
				new RSApiClassVisitor(apiClass),
				ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
			);

			classes.add(apiClass);
		}
	}
}
