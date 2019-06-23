package com.hlag.oversigt.web.api;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import edu.umd.cs.findbugs.annotations.Nullable;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonConfig implements ContextResolver<ObjectMapper> {
	private final ObjectMapper objectMapper;

	@Inject
	public JacksonConfig(final ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public ObjectMapper getContext(@SuppressWarnings("unused") @Nullable final Class<?> objectType) {
		return objectMapper;
	}
}
