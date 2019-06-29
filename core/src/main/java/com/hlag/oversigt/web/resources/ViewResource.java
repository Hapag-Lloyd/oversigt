package com.hlag.oversigt.web.resources;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.CaseFormat;
import com.google.common.io.Resources;
import com.google.inject.Singleton;
import com.hlag.oversigt.util.Wro4jExecutor;
import com.hlag.oversigt.web.api.ErrorResponse;
import com.hlag.oversigt.web.api.NoChangeLog;

import de.larssh.utils.SneakyException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import ro.isdc.wro.model.resource.ResourceType;

@Api(tags = { "View" })
@Path("/views/{viewId}")
@Singleton
public class ViewResource {
	public ViewResource() {
		// no fields to be initialized
	}

	@GET
	@Path("/html")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returning the raw HTML content of the view"),
			@ApiResponse(code = 404, message = "The view does not exist") })
	@ApiOperation(value = "Read the HTML data of the given widget", produces = "text/html")
	@PermitAll
	@NoChangeLog
	public Response readHtml(@PathParam("viewId") @NotNull @NotBlank final String viewId) {
		if (doesViewExist(viewId)) {
			final Optional<String> content = readContent(getResourceUrl(viewId, "html"));
			if (content.isPresent()) {
				return Response.ok(content.get(), MediaType.TEXT_HTML_TYPE).build();
			}
		}
		return ErrorResponse.notFound("View does not exist.");

	}

	@GET
	@Path("/css")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returning the raw CSS content of the view"),
			@ApiResponse(code = 404, message = "The view does not exist") })
	@ApiOperation(value = "Read the CSS data of the given widget", produces = "text/css")
	@PermitAll
	@NoChangeLog
	public Response readCss(@PathParam("viewId") @NotNull @NotBlank final String viewId) {
		if (doesViewExist(viewId)) {
			final Optional<String> out = new Wro4jExecutor(getName(viewId), true).execute(ResourceType.CSS);
			if (out.isPresent()) {
				return Response.ok(out.get(), "text/css").build();
			}
			return ErrorResponse.internalServerError(UUID.randomUUID(), "Unable to generate view content");
		}
		return ErrorResponse.notFound("View does not exist.");
	}

	@GET
	@Path("/js")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Returning the raw Javascript content of the view"),
			@ApiResponse(code = 404, message = "The view does not exist") })
	@ApiOperation(value = "Read the Javascript data of the given widget", produces = "application/javascript")
	@PermitAll
	@NoChangeLog
	public Response readJavascript(@PathParam("viewId") @NotNull @NotBlank final String viewId) {
		if (doesViewExist(viewId)) {
			final Optional<String> out = new Wro4jExecutor(getName(viewId), true).execute(ResourceType.JS);
			if (out.isPresent()) {
				return Response.ok(out.get(), "application/javascript").build();
			}
			return ErrorResponse.internalServerError(UUID.randomUUID(), "Unable to generate view content");
		}
		return ErrorResponse.notFound("View does not exist.");
	}

	private static String getName(final String viewId) {
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, viewId);
	}

	private static boolean doesViewExist(final String viewId) {
		try {
			return new File(getResourceUrl(viewId, "html").toURI()).exists();
		} catch (final URISyntaxException e) {
			throw new SneakyException(e);
		}
	}

	private static URL getResourceUrl(final String viewId, final String extension) {
		return Resources.getResource("statics/widgets/" + getName(viewId) + "/" + getName(viewId) + "." + extension);
	}

	private static Optional<String> readContent(final URL url) {
		try {
			return Optional.of(new String(Resources.toByteArray(url), StandardCharsets.UTF_8));
		} catch (@SuppressWarnings("unused") final FileNotFoundException ignore) {
			return Optional.empty();
		} catch (final Exception e) {
			throw new SneakyException(e);
		}
	}
}
