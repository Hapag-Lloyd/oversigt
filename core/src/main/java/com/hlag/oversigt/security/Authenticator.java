package com.hlag.oversigt.security;

public interface Authenticator extends AutoCloseable {
	Principal login(String username, String password);

	boolean isUsernameValid(String username);

	void reloadRoles(String username);

	Principal readPrincipal(String username);

	public static class NoAuthenticator implements Authenticator {
		@Override
		public Principal login(String username, String password) {
			return null;
		}

		@Override
		public Principal readPrincipal(String username) {
			return null;
		}

		@Override
		public boolean isUsernameValid(String username) {
			return false;
		}

		@Override
		public void reloadRoles(String username) {
		}

		@Override
		public void close() {
		}
	}
}
