package com.hlag.oversigt.security;

import static java.util.stream.Collectors.toSet;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;

/**
 * @author neumaol
 * @see <a href="https://www.baeldung.com/jboss-undertow">Baeldung</a>
 */
@Singleton
public class OversigtIdentityManager implements IdentityManager {
	@Inject
	private Authenticator authenticator;

	OversigtIdentityManager() {
		// nothing to do
	}

	@Override
	@Nullable
	public Account verify(@Nullable final Account account) {
		return account;
	}

	@Override
	@Nullable
	public Account verify(@SuppressWarnings("unused") @Nullable final Credential credential) {
		return null;
	}

	@Override
	@Nullable
	public Account verify(@Nullable final String id, @Nullable final Credential credential) {
		if (credential instanceof PasswordCredential) {
			final Optional<Principal> principal = authenticator.login(Objects.requireNonNull(id),
					new String(((PasswordCredential) credential).getPassword()));
			return principal.map(OversigtAccount::new).orElse(null);
		}
		return null;
	}

	private final class OversigtAccount implements Account {
		private static final long serialVersionUID = 2402012940268021227L;

		private final Principal principal;

		OversigtAccount(final Principal principal) {
			this.principal = principal;
		}

		@Override
		public Principal getPrincipal() {
			return principal;
		}

		@Override
		public Set<String> getRoles() {
			return principal.getRoles().stream().map(Object::toString).collect(toSet());
		}
	}
}
