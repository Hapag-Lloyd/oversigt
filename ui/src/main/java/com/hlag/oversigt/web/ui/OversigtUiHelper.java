package com.hlag.oversigt.web.ui;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import de.larssh.utils.Nullables;
import de.larssh.utils.io.Resources;

/**
 * Class containing information to get to the Oversigt UI.
 *
 * @author neumaol
 *
 */
public final class OversigtUiHelper {
	/** Hidden constructor so nobody can instantiate this */
	private OversigtUiHelper() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Gets the URI to the Oversigt UI.
	 *
	 * @return the URI to the Oversigt UI if found - otherwise empty()
	 */
	public static Optional<URI> getPathToUiResources() {
		final ClassLoader classLoader = Nullables.orElseGet(Thread.currentThread().getContextClassLoader(),
				ClassLoader::getSystemClassLoader);
		return Resources.getResource(classLoader, Paths.get("oversigt-ui")).map(Path::toUri);
	}
}
