import com.openosrs.injector.InjectPlugin;
import com.openosrs.injector.Injection;
import com.openosrs.injector.injection.InjectTaskHandler;
import java.io.File;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

public class TTest
{
	private static final File VAN = new File("C:\\Users\\Lucas\\.gradle\\caches\\modules-2\\files-2.1\\net.runelite.rs\\vanilla\\184\\1bdb54d90d696598a8ee5ff793155482970180a\\vanilla-184.jar");
	private static final Project project = ProjectBuilder.builder().withProjectDir(new File("C:\\Users\\Lucas\\IdeaProjects\\runelite")).build();
	private static final FileTree API = project.zipTree("/runescape-api/build/libs/runescape-api-1.5.37-SNAPSHOT.jar"),
		DEOB = project.zipTree("/runescape-client/build/libs/rs-client-1.5.37-SNAPSHOT.jar"),
		MIXINS = project.zipTree("/runelite-mixins/build/libs/mixins-1.5.37-SNAPSHOT.jar");


	@Test
	public void test() throws Exception
	{
		InjectTaskHandler inj = new Injection(VAN, DEOB, API, MIXINS);

		inj.inject();
	}
}
