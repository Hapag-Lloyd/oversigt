import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "staging", requiresDirectInvocation = true, requiresProject = false)
public class StagingMojo extends AbstractMojo {
	/** {@inheritDoc} */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		// Dummy Mojo to fix build problems
	}
}
