package com.hlag.oversigt.security;

import static com.hlag.oversigt.util.Utils.notNullOrEmpty;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import edu.umd.cs.findbugs.annotations.Nullable;

@Singleton
public class LdapAuthenticator implements Authenticator {
	private static final Logger LOGGER = LoggerFactory.getLogger(LdapAuthenticator.class);

	private final String url;

	private final String bindUser;

	private final String bindPassword;

	private final String baseDn;

	private final String identifyingAttribute;

	@Nullable
	private DirContext serviceCtx = null;

	@Inject
	private RoleProvider roleProvider;

	@Inject
	public LdapAuthenticator(final LdapConfiguration configuration) {
		this(configuration.url,
				configuration.baseDn,
				configuration.bindUser,
				configuration.bindPassword,
				configuration.uidAttribute);
	}

	private LdapAuthenticator(@Nullable final String url,
			@Nullable final String baseDn,
			@Nullable final String bindUser,
			@Nullable final String bindPassword,
			@Nullable final String uidAttribute) {
		this.url = notNullOrEmpty(url, "The LDAP URL must not be null or empty");
		this.baseDn = notNullOrEmpty(baseDn, "The Base-DN must not be null or empty");
		identifyingAttribute = notNullOrEmpty(uidAttribute, "The LDAP identifying attribute must not be null or empty");
		this.bindUser = notNullOrEmpty(bindUser, "The LDAP Bind user must not be null or empty");
		this.bindPassword = notNullOrEmpty(bindPassword, "The LDAP Bind password must not be null or empty");

		try {
			setDirContext();
		} catch (final NamingException e) {
			LOGGER.error("Unable to create Directory Context", e);
			throw new RuntimeException("Unable to create Directory Context", e);
		}
	}

	private DirContext setDirContext() throws NamingException {
		if (serviceCtx != null) {
			try {
				serviceCtx.close();
			} catch (final Exception e) {
				LOGGER.warn("Exception while closing old Service Context", e);
			}
		}

		final Properties serviceEnv = new Properties();
		serviceEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		serviceEnv.put(Context.PROVIDER_URL, url);
		serviceEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
		serviceEnv.put(Context.SECURITY_PRINCIPAL, bindUser);
		serviceEnv.put(Context.SECURITY_CREDENTIALS, bindPassword);

		serviceCtx = new InitialDirContext(serviceEnv);
		return serviceCtx;
	}

	@Override
	public Optional<Principal> login(final String username, final String password) {
		Objects.requireNonNull(username, "Username must not be null");
		Objects.requireNonNull(password, "Password must not be null");

		try {
			final Optional<Principal> distinguishedName = readDistinguishedName(username);
			if (distinguishedName.isPresent()) {
				if (authorize(distinguishedName.get().getDistinguishedName(), password)) {
					return distinguishedName;
				}
			}
		} catch (final NamingException e) {
			LOGGER.error("Unable to log in user", e);
		}
		return Optional.empty();
	}

	@Override
	public Optional<Principal> readPrincipal(final String username) {
		try {
			return readDistinguishedName(username);
		} catch (final NamingException e) {
			throw new RuntimeException("Unable to read user information for username: " + username, e);
		}
	}

	@Override
	public boolean isUsernameValid(final String username) {
		if (Strings.isNullOrEmpty(username)) {
			return false;
		}
		try {
			return readDistinguishedName(username).isPresent();
		} catch (final NamingException e) {
			LOGGER.error("Unable to check username validity", e);
			return false;
		}
	}

	private Optional<Principal> readDistinguishedName(final String username) throws NamingException {
		final String[] attributeFilter = { identifyingAttribute, "distinguishedName", "sn", "givenname", "mail" };
		final SearchControls sc = new SearchControls();
		sc.setReturningAttributes(attributeFilter);
		sc.setSearchScope(SearchControls.SUBTREE_SCOPE);

		// use a search filter to find only the user we want to authenticate
		final String searchFilter = "(" + identifyingAttribute + "=" + username + ")";
		NamingEnumeration<SearchResult> results;
		if (serviceCtx != null) {
			try {
				results = serviceCtx.search(baseDn, searchFilter, sc);
			} catch (final NamingException e) {
				if (e.getCause() instanceof IOException && "connection closed".equals(e.getCause().getMessage())) {
					LOGGER.info("Connection broken. Trying to create a new connection.");
					try {
						results = setDirContext().search(baseDn, searchFilter, sc);
					} catch (final NamingException namingException) {
						throw new RuntimeException("Connection was closed and now unable to create a new context",
								namingException);
					}
				} else {
					LOGGER.warn("Unknown exception type. Unable to handle it.");
					throw e;
				}
			}
		} else {
			return Optional.empty();
		}

		if (!results.hasMore()) {
			return Optional.empty();
		}

		// get the users DN (distinguishedName) from the result
		final SearchResult result = results.next();
		final Attributes attributes = result.getAttributes();

		return Optional.of(new Principal(result.getNameInNamespace(),
				username,
				attributes.get("givenname").get().toString() + " " + attributes.get("sn").get().toString(),
				attributes.get("mail").get().toString(),
				roleProvider.getRoles(username)));
	}

	private boolean authorize(final String distinguishedName, final String password) {
		try {
			// attempt another authentication, now with the user
			final Properties authEnv = new Properties();
			authEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
			authEnv.put(Context.PROVIDER_URL, url);
			authEnv.put(Context.SECURITY_PRINCIPAL, distinguishedName);
			authEnv.put(Context.SECURITY_CREDENTIALS, password);
			new InitialDirContext(authEnv).close();
			return true;
		} catch (@SuppressWarnings("unused") final Exception e) {
			try {
				// Sleep to slow down responses for brute force attacks
				Thread.sleep(1000);
			} catch (@SuppressWarnings("unused") final InterruptedException ignore) {
				// on interruption continue
			}
			return false;
		}
	}

	@Override
	public void reloadRoles(final String username) {
		Objects.requireNonNull(username);
		Principal.getPrincipal(username).ifPresent(p -> p.changeRoles(roleProvider.getRoles(username)));
	}

	@Override
	public void close() {
		if (serviceCtx != null) {
			try {
				serviceCtx.close();
			} catch (final NamingException e) {
				throw new RuntimeException("Unable to close directory context", e);
			}
		}
	}

	public static class LdapConfiguration {
		@Nullable
		private String url;

		@Nullable
		private String baseDn;

		@Nullable
		private String bindUser;

		@Nullable
		private String bindPassword;

		@Nullable
		private String uidAttribute;

		public LdapConfiguration() {
			// no fields to be initialized manually, some will be injected
		}

		public boolean isBindPasswordSet() {
			return !Strings.isNullOrEmpty(bindPassword);
		}

		public void setBindPassword(final String bindPassword) {
			this.bindPassword = bindPassword;
		}
	}
}
