package com.hlag.oversigt.web.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.ws.rs.core.Application;

import com.fasterxml.jackson.databind.JavaType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.core.OversigtServer;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.web.resources.Authentication;

import io.swagger.converter.ModelConverter;
import io.swagger.converter.ModelConverterContext;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Model;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.PropertyBuilder;
import io.swagger.util.Json;

@Singleton
public class ApiApplication extends Application {

	@Inject
	public ApiApplication(@Named("hostname") String hostname) {
		// Initialize Swagger settings
		URL url;
		try {
			url = new URL(hostname);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		// Configure Swagger
		BeanConfig beanConfig = new BeanConfig();
		beanConfig.setSchemes(new String[] { url.getProtocol() });
		beanConfig.setHost(url.getHost()
				+ (url.getPort() != -1 && url.getPort() != url.getDefaultPort() ? ":" + url.getPort() : ""));
		beanConfig.setBasePath(OversigtServer.MAPPING_API);
		beanConfig.setResourcePackage(
				Authentication.class.getPackage().getName() + "," + Api.class.getPackage().getName());
		beanConfig.setScan(true);

		// Change model display for swagger
		ModelConverters.getInstance().addConverter(new ColorConverter());
		ModelConverters.getInstance().addConverter(new DurationConverter());
	}

	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> classes = new HashSet<>();

		// Add Swagger Resources
		classes.add(io.swagger.jaxrs.listing.ApiListingResource.class);
		classes.add(io.swagger.jaxrs.listing.SwaggerSerializers.class);

		// All other resources will be found by Guice module in ApiBootstrapListener

		return classes;
	}

	private static class ColorConverter implements ModelConverter {
		@Override
		public Property resolveProperty(Type type,
				ModelConverterContext context,
				Annotation[] annotations,
				Iterator<ModelConverter> chain) {
			final JavaType jType = Json.mapper().constructType(type);
			if (jType != null) {
				final Class<?> cls = jType.getRawClass();
				if (cls.equals(Color.class)) {
					HashMap<PropertyBuilder.PropertyId, Object> map = new HashMap<>();
					//map.put(PropertyBuilder.PropertyId.FORMAT, "#rrggbbaa");
					map.put(PropertyBuilder.PropertyId.TYPE, "string");
					map.put(PropertyBuilder.PropertyId.EXAMPLE, "#11223344");
					return PropertyBuilder.build("string", "#rrggbbaa", map);
				} else {
					return chain.next().resolveProperty(type, context, annotations, chain);
				}
			} else {
				return null;
			}
		}

		@Override
		public Model resolve(Type type, ModelConverterContext context, Iterator<ModelConverter> chain) {
			return chain.next().resolve(type, context, chain);
		}
	}

	private static class DurationConverter implements ModelConverter {
		@Override
		public Property resolveProperty(Type type,
				ModelConverterContext context,
				Annotation[] annotations,
				Iterator<ModelConverter> chain) {
			final JavaType jType = Json.mapper().constructType(type);
			if (jType != null) {
				final Class<?> cls = jType.getRawClass();
				if (cls.equals(Duration.class)) {
					HashMap<PropertyBuilder.PropertyId, Object> map = new HashMap<>();
					//map.put(PropertyBuilder.PropertyId.FORMAT, "#rrggbbaa");
					map.put(PropertyBuilder.PropertyId.TYPE, "string");
					map.put(PropertyBuilder.PropertyId.EXAMPLE, "PT1H");
					map.put(PropertyBuilder.PropertyId.PATTERN,
							"^P(?:(0+)Y)?(?:(0+)M)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$");
					return PropertyBuilder.build("string", null, map);
				} else {
					return chain.next().resolveProperty(type, context, annotations, chain);
				}
			} else {
				return null;
			}
		}

		@Override
		public Model resolve(Type type, ModelConverterContext context, Iterator<ModelConverter> chain) {
			return chain.next().resolve(type, context, chain);
		}
	}
}