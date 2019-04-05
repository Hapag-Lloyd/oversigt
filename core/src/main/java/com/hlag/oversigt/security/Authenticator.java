package com.hlag.oversigt.security;

public interface Authenticator extends AutoCloseable {
	Principal login(String username, String password);

	boolean isUsernameValid(String username);

	void reloadRoles(String username);

	Principal readPrincipal(String username);

	public class NoAuthenticator implements Authenticator {
		@Override
		public Principal login(final String username, final String password) {
			return null;
		}

		@Override
		public Principal readPrincipal(final String username) {
			return null;
		}

		@Override
		public boolean isUsernameValid(final String username) {
			return false;
		}

		@Override
		public void reloadRoles(final String username) {}

		@Override
		public void close() {}
	}
}
