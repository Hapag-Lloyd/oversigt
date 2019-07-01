package com.hlag.oversigt.web.api;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.plugins.interceptors.CorsFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Feature to enable CORS in debug mode to enable development of web UIs
 *
 * @see <a href=
 *      "https://stackoverflow.com/questions/36511330/resteasy-and-corsfilter-implementation">StackOverflow</a>
 */
@Provider
public class CorsFeature implements Feature {
	private static final Logger LOGGER = LoggerFactory.getLogger(CorsFeature.class);

	@Inject
	@Named("debug")
	private boolean debug;

	public CorsFeature() {
		// no fields to be initialized manually, some will be injected
	}

	/** {@inheritDoc} */
	@Override
	public boolean configure(@Nullable final FeatureContext context) {
		if (debug && context != null) {
			LOGGER.warn("Debug mode: Enabling CORS to permit calls from other hosts.");
			final CorsFilter corsFilter = new CorsFilter();
			corsFilter.getAllowedOrigins().add("*");
			context.register(corsFilter);
		} else {
			LOGGER.info("CORS will not be enabled in order to prevent calls from other servers.");
		}
		return true;
	}
}
