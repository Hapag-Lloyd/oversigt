package com.hlag.oversigt.security;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class MapAuthenticator implements Authenticator {
	private static final Logger LOGGER = LoggerFactory.getLogger(MapAuthenticator.class);

	private final Map<String, String> usernamesToPasswords;

	@Inject
	private RoleProvider roleProvider;

	@Inject
	public MapAuthenticator(@Named("UsernamesAndPasswords") Map<String, String> usernamesToPasswords) {
		if (Objects.requireNonNull(usernamesToPasswords, "Username/Password map must not be null").isEmpty()) {
			LOGGER.warn("Username/Password map does not contain entries. No log in possible.");
		}
		this.usernamesToPasswords = new HashMap<>(usernamesToPasswords);
	}

	@Override
	public Principal login(String username, String password) {
		Objects.requireNonNull(username, "Username must not be null");
		Objects.requireNonNull(password, "Password must not be null");

		String savedPassword = usernamesToPasswords.get(username);
		if (password.equals(savedPassword)) {
			return new Principal(username, roleProvider.getRoles(username));
		} else {
			return null;
		}
	}

	@Override
	public Principal readPrincipal(String username) {
		return new Principal(username, new HashSet<>());
	}

	@Override
	public boolean isUsernameValid(String username) {
		return usernamesToPasswords.containsKey(username);
	}

	@Override
	public void reloadRoles(String username) {
		Objects.requireNonNull(username);
		Principal.getPrincipal(username).ifPresent(p -> p.changeRoles(roleProvider.getRoles(username)));
	}

	@Override
	public void close() {
	}
}
