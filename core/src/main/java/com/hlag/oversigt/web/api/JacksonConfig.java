package com.hlag.oversigt.web.api;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonConfig implements ContextResolver<ObjectMapper> {
	private final ObjectMapper objectMapper;

	@Inject
	public JacksonConfig(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public ObjectMapper getContext(Class<?> objectType) {
		return objectMapper;
	}
}