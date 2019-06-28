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

@Singleton
public class LdapAuthenticator implements Authenticator {
	private static final Logger LOGGER = LoggerFactory.getLogger(LdapAuthenticator.class);

	private final String url;

	private final String bindUser;

	private final String bindPassword;

	private final String baseDn;

	private final String identifyingAttribute;

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

	private LdapAuthenticator(final String url,
			final String baseDn,
			final String bindUser,
			final String bindPassword,
			final String uidAttribute) {
		notNullOrEmpty(url, "The LDAP URL must not be null or empty");
		notNullOrEmpty(baseDn, "The Base-DN must not be null or empty");
		notNullOrEmpty(uidAttribute, "The LDAP identifying attribute must not be null or empty");
		notNullOrEmpty(bindUser, "The LDAP Bind user must not be null or empty");
		notNullOrEmpty(bindPassword, "The LDAP Bind password must not be null or empty");

		this.url = url;
		this.bindUser = bindUser;
		this.bindPassword = bindPassword;
		this.baseDn = baseDn;
		identifyingAttribute = uidAttribute;

		try {
			setDirContext();
		} catch (final NamingException e) {
			LOGGER.error("Unable to create Directory Context", e);
			throw new RuntimeException("Unable to create Directory Context", e);
		}
	}

	private void setDirContext() throws NamingException {
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
	}

	@Override
	public Principal login(final String username, final String password) {
		Objects.requireNonNull(username, "Username must not be null");
		Objects.requireNonNull(password, "Password must not be null");

		try {
			final Optional<Principal> distinguishedName = readDistinguishedName(username);
			if (distinguishedName.isPresent()) {
				if (authorize(distinguishedName.get().getDistinguishedName(), password)) {
					return distinguishedName.get();
				}
			}
		} catch (final NamingException e) {
			LOGGER.error("Unable to log in user", e);
		}
		return null;
	}

	@Override
	public Principal readPrincipal(final String username) {
		try {
			return readDistinguishedName(username).get();
		} catch (final Exception e) {
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
		try {
			results = serviceCtx.search(baseDn, searchFilter, sc);
		} catch (final NamingException e) {
			if (e.getCause() instanceof IOException && "connection closed".equals(e.getCause().getMessage())) {
				LOGGER.info("Connection broken. Trying to create a new connection.");
				try {
					setDirContext();
					results = serviceCtx.search(baseDn, searchFilter, sc);
				} catch (final NamingException namingException) {
					throw new RuntimeException("Connection was closed and now unable to create a new context",
							namingException);
				}
			} else {
				LOGGER.warn("Unknown exception type. Unable to handle it.");
				throw e;
			}
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
		} catch (final Exception e) {
			try {
				// Sleep to slow down responses for brute force attacks
				Thread.sleep(1000);
			} catch (final InterruptedException ignore) {
				// empty by design
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
		private String url;

		private String baseDn;

		private String bindUser;

		private String bindPassword;

		private String uidAttribute;

		public LdapConfiguration() {
			// empty by design
		}

		public boolean isBindPasswordSet() {
			return !Strings.isNullOrEmpty(bindPassword);
		}

		public void setBindPassword(final String bindPassword) {
			this.bindPassword = bindPassword;
		}
	}
}
