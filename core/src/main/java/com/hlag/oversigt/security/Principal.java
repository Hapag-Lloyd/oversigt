package com.hlag.oversigt.security;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Principal implements java.security.Principal {
	private static final Map<String, Principal> PRINCIPALS = Collections.synchronizedMap(new WeakHashMap<>());

	static Optional<Principal> getPrincipal(final String username) {
		return Optional.ofNullable(PRINCIPALS.get(username));
	}

	/**
	 * Gets the principal with the given username from the given
	 * {@link Authenticator}.
	 *
	 * @param authenticator the {@link Authenticator} to use for reading the
	 *                      principal information
	 * @param username      the name of the {@link Principal} to read
	 * @return the read {@link Principal} object. This method does not return
	 *         <code>null</code>.
	 * @throws java.util.NoSuchElementException if the principal could not be
	 *                                          loaded.
	 */
	public static Principal loadPrincipal(final Authenticator authenticator, final String username) {
		PRINCIPALS.computeIfAbsent(username, name -> {
			final Principal principal = authenticator.readPrincipal(name);
			authenticator.reloadRoles(name);
			return principal;
		});
		return getPrincipal(username).get();
	}

	private final String distinguishedName;

	private final String username;

	private final String name;

	private final String email;

	private final Set<Role> roles = new HashSet<>();

	// TODO make private and use cached principals
	Principal(final String username, final Set<Role> roles) {
		this(username, username, username, username + "@example.com", roles);
	}

	Principal(final String distinguishedName,
			final String username,
			final String name,
			final String email,
			final Set<Role> roles) {
		this.distinguishedName = distinguishedName;
		this.username = username;
		this.name = name;
		this.email = email;
		this.roles.addAll(roles);

		PRINCIPALS.put(username, this);
	}

	synchronized void changeRoles(final Collection<Role> newRoles) {
		roles.clear();
		roles.addAll(newRoles);
	}

	@Override
	public String getName() {
		return name;
	}

	String getDistinguishedName() {
		return distinguishedName;
	}

	public String getEmail() {
		return email;
	}

	public String getUsername() {
		return username;
	}

	public Set<Role> getRoles() {
		return Collections.unmodifiableSet(roles);
	}

	public synchronized boolean hasRole(final Role role) {
		for (Role currentRole = role; currentRole != null; currentRole = currentRole.getParent()) {
			if (roles.contains(currentRole)) {
				return true;
			}
		}
		return false;
	}

	public boolean hasRole(final String roleName) {
		final Optional<Roles> roles = Roles.maybeFromString(roleName);
		if (roles.isPresent()) {
			return hasRole(roles.get().getRole());
		}

		final String[] parts = roleName.toLowerCase().split("\\.", 3);
		if (parts.length == 3 && parts[0].equals("dashboard")) {
			switch (parts[2]) {
			case "owner":
				return hasRole(Role.DASHBOARD_OWNER.getDashboardSpecificRole(parts[1]));
			case "editor":
				return hasRole(Role.DASHBOARD_EDITOR.getDashboardSpecificRole(parts[1]));
			default:
				// unknown part
				return false;
			}
		}
		// does not fit current role names
		return false;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
	}
}
