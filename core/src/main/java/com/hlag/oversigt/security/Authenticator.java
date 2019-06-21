package com.hlag.oversigt.security;

import java.util.Optional;

public interface Authenticator extends AutoCloseable {
	Optional<Principal> login(String username, String password);

	boolean isUsernameValid(String username);

	void reloadRoles(String username);

	Optional<Principal> readPrincipal(String username);

	class NoAuthenticator implements Authenticator {
		@Override
		public Optional<Principal> login(@SuppressWarnings("unused") final String username,
				@SuppressWarnings("unused") final String password) {
			return Optional.empty();
		}

		@Override
		public Optional<Principal> readPrincipal(@SuppressWarnings("unused") final String username) {
			return Optional.empty();
		}

		@Override
		public boolean isUsernameValid(@SuppressWarnings("unused") final String username) {
			return false;
		}

		@Override
		public void reloadRoles(@SuppressWarnings("unused") final String username) {/* do nothing */}

		@Override
		public void close() {/* do nothing */}
	}
}
