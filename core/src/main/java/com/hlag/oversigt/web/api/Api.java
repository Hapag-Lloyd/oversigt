package com.hlag.oversigt.web.api;

import javax.ws.rs.core.MediaType;

import io.swagger.annotations.ApiKeyAuthDefinition;
import io.swagger.annotations.ApiKeyAuthDefinition.ApiKeyLocation;
import io.swagger.annotations.Info;
import io.swagger.annotations.SecurityDefinition;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

/**
 * This class is used to define basic functionality of the Oversigt API. It does
 * not contain logic.
 *
 * @author Olaf Neumann
 *
 */
@SwaggerDefinition( //
		info = @Info(//
				title = "Oversigt API", //
				version = "1.0", //
				description = "This API provides access to all public operations of Oversigt." //
		), //
		tags = { //
				@Tag(name = "Authentication", description = "Everything around authentication"), //
				@Tag(name = "Dashboard", description = "Control dashboards"), //
				@Tag(name = "Dashboard-Widget", description = "Control widgets"), //
				@Tag(name = "EventSource", description = "Control event sources"), //
				@Tag(name = "SerializableValue", description = "Control serializable values"), //
				@Tag(name = "System", description = "General system functions") //
		}, //
		securityDefinition = @SecurityDefinition(//
				apiKeyAuthDefinitions = {
						@ApiKeyAuthDefinition(key = "JsonWebToken",
								in = ApiKeyLocation.HEADER,
								name = "Authorization",
								description = "This authorization definition uses JSON Web Tokens. To correctly use it please prepend a 'Bearer ' to the token.") //
				} //
		), //
		consumes = { MediaType.APPLICATION_JSON }, //
		produces = { MediaType.APPLICATION_JSON } //
)
public class Api {

}
