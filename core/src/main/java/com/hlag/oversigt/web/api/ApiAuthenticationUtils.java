package com.hlag.oversigt.web.api;

import static com.hlag.oversigt.core.HttpHandlers.MAPPING_API;

import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

/**
 * @author Olaf Neumann see
 *         https://stackoverflow.com/questions/26777083/best-practice-for-rest-token-based-authentication-with-jax-rs-and-jersey
 *
 */
@Singleton
public class ApiAuthenticationUtils {
	private final String issuer;

	private final byte[] apiSecret;

	private final SignatureAlgorithm signatureAlgorithm;

	private final long apiTtl;

	private final Authenticator authenticator;

	@Inject
	public ApiAuthenticationUtils(@Named("hostname") final String hostname,
			@Named("api.secret.base64") final String apiSecretBase64,
			final Authenticator authenticator,
			final SignatureAlgorithm signatureAlgorithm,
			@Named("api.ttl") final long ttl) {
		issuer = hostname + MAPPING_API;
		apiSecret = Base64.getDecoder().decode(apiSecretBase64);
		this.signatureAlgorithm = signatureAlgorithm;
		apiTtl = ttl;
		this.authenticator = authenticator;
	}

	public Principal authenticate(final String username, final String password) throws Exception {
		// Authenticate against a database, LDAP, file or whatever
		// Throw an Exception if the credentials are invalid

		return authenticator//
				.login(username, password)
				.orElseThrow(() -> new RuntimeException("Unable to log in"));
	}

	public String issueToken(final Principal principal) throws IllegalArgumentException {
		final long nowMillis = System.currentTimeMillis();
		final Date now = new Date(nowMillis);
		final String id = nowMillis + "-" + UUID.randomUUID().toString();

		final JwtBuilder builder = Jwts.builder()
				.setId(id)
				.setIssuedAt(now)
				.setSubject("oversigt-api")
				.setIssuer(issuer)
				.claim("username", principal.getUsername())
				.signWith(signatureAlgorithm, apiSecret);

		if (apiTtl > 0) {
			final long expMillis = nowMillis + apiTtl;
			final Date exp = new Date(expMillis);
			builder.setExpiration(exp);
		}

		return builder.compact();
	}

	public Principal validateToken(final String token)
			throws ExpiredJwtException, UnsupportedJwtException, MalformedJwtException, SignatureException {
		// Check if the token was issued by the server and if it's not expired
		// Throw an Exception if the token is invalid

		// This line will throw an exception if it is not a signed JWS (as expected)
		final Claims claims = Jwts.parser()
				.setSigningKey(apiSecret)
				.requireSubject("oversigt-api")
				.requireIssuer(issuer)
				.parseClaimsJws(token)
				.getBody();

		if (claims.getExpiration().before(new Date())) {
			throw new RuntimeException("JWT expired");
		}

		return Principal.loadPrincipal(authenticator, claims.get("username", String.class)).get();
	}
}
