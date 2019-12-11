package com.hlag.oversigt.security;

import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.controller.DashboardController;
import com.hlag.oversigt.storage.Storage;

@Singleton
public class RoleProvider {
	@Inject
	@Named("serverAdmins")
	private List<String> adminUserIds;

	@Inject
	private Storage storage;

	@Inject
	private DashboardController controller;

	public RoleProvider() {
		// no fields to be initialized manually, some will be injected
	}

	public Set<Role> getRoles(final String userid) {
		final Set<Role> roles = new HashSet<>();

		if (adminUserIds.contains(userid.toLowerCase().trim())) {
			roles.add(Role.SERVER_ADMIN);
			roles.add(Role.DASHBOARD_OWNER);
			roles.add(Role.DASHBOARD_EDITOR);
			addWithDefaultRole(roles,
					controller.getDashboardIds().stream().map(Role::getDashboardOwnerRole),
					Role.DASHBOARD_OWNER);
			addWithDefaultRole(roles,
					controller.getDashboardIds().stream().map(Role::getDashboardEditorRole),
					Role.DASHBOARD_EDITOR);
		} else {
			addWithDefaultRole(roles,
					storage.getOwnedDashboardIds(userid).stream().map(Role::getDashboardOwnerRole),
					Role.DASHBOARD_OWNER);
			addWithDefaultRole(roles,
					storage.getOwnedDashboardIds(userid).stream().map(Role::getDashboardEditorRole),
					Role.DASHBOARD_EDITOR);

			addWithDefaultRole(roles,
					storage.getEditableDashboardIds(userid).stream().map(Role::getDashboardEditorRole),
					Role.DASHBOARD_EDITOR);
		}

		return roles;
	}

	private static void addWithDefaultRole(final Collection<Role> roles,
			final Stream<Role> rolesToAdd,
			final Role defaultRole) {
		addWithDefaultRole(roles, rolesToAdd.collect(toSet()), defaultRole);
	}

	private static void addWithDefaultRole(final Collection<Role> roles,
			final Collection<Role> rolesToAdd,
			final Role defaultRole) {
		if (!rolesToAdd.isEmpty()) {
			roles.addAll(rolesToAdd);
			roles.add(defaultRole);
		}
	}
}
