package com.hlag.oversigt.web.ui;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import com.google.common.io.Resources;

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
		try {
			return Optional.of(Resources.getResource("oversigt-ui").toURI());
		} catch (@SuppressWarnings("unused") final IllegalArgumentException | URISyntaxException ignore) {
			return Optional.empty();
		}
	}
}
