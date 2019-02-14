package com.hlag.oversigt.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.storage.Storage;

@Singleton
public class RoleProvider {
	@Inject
	@Named("serverAdmins")
	private List<String> adminUserIds;
	@Inject
	private Storage storage;

	public Set<Role> getRoles(String userid) {
		Set<Role> roles = new HashSet<>();

		if (adminUserIds.contains(userid.toLowerCase().trim())) {
			roles.add(Role.SERVER_ADMIN);
		}

		addWithDefaultRole(roles,
				storage.getOwnedDashboardIds(userid)
						.stream()
						.map(Role::getDashboardOwnerRole)
						.collect(Collectors.toSet()),
				Role.DASHBOARD_OWNER);
		addWithDefaultRole(roles,
				storage.getOwnedDashboardIds(userid)
						.stream()
						.map(Role::getDashboardEditorRole)
						.collect(Collectors.toSet()),
				Role.DASHBOARD_EDITOR);

		addWithDefaultRole(roles,
				storage.getEditableDashboardIds(userid)
						.stream()
						.map(Role::getDashboardEditorRole)
						.collect(Collectors.toSet()),
				Role.DASHBOARD_EDITOR);

		return roles;
	}

	private static void addWithDefaultRole(Collection<Role> roles, Collection<Role> rolesToAdd, Role defaultRole) {
		if (rolesToAdd != null && !rolesToAdd.isEmpty()) {
			roles.addAll(rolesToAdd);
			roles.add(defaultRole);
		}
	}
}
