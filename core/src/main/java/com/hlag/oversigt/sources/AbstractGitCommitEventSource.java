package com.hlag.oversigt.sources;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.revwalk.RevCommit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.sources.data.JsonHint;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

public abstract class AbstractGitCommitEventSource<E extends OversigtEvent> extends AbstractGitEventSource<E> {
	private int limit = 5;

	private String[] usersToSuppress = new String[0];

	private NameMapping[] nameMappings = new NameMapping[0];

	@Property(name = "Limit",
			description = "The maximum number of committers to display. Values below 1 display all committers.")
	public int getLimit() {
		return limit;
	}

	public void setLimit(final int limit) {
		this.limit = limit;
	}

	@Property(name = "Users to suppress",
			description = "If you want to suppress certain names, enter them here. This may be useful to prevent technical users from being shown in the list of committers.")
	@JsonHint(arrayStyle = ArrayStyle.TABLE)
	public String[] getUsersToSuppress() {
		return usersToSuppress;
	}

	public void setUsersToSuppress(final String[] usersToSuppress) {
		this.usersToSuppress = usersToSuppress;
	}

	@Property(name = "Name mappings",
			description = "Some users have different user names. Use these mappings to map all the known user names to one single name.")
	public NameMapping[] getNameMappings() {
		return nameMappings;
	}

	public void setNameMappings(final NameMapping[] nameMappings) {
		this.nameMappings = nameMappings;
	}

	protected <R> R streamLogWithoutFilteredUsers(final Function<Stream<RevCommit>, R> function)
			throws NoHeadException, GitAPIException, IOException {
		final Set<String> usersToSuppress = new HashSet<>(Arrays.asList(getUsersToSuppress()));
		return streamLog(
				s -> function.apply(s.filter(rv -> !usersToSuppress.contains(rv.getCommitterIdent().getName()))));
	}

	protected Function<String, String> createNameMapper() {
		final Map<String, String> nameMap
				= Arrays.stream(getNameMappings()).collect(toMap(NameMapping::getName, NameMapping::getMapTo));
		return x -> nameMap.computeIfAbsent(x, Function.identity());
	}

	@JsonHint(headerTemplate = "{{self.name}}", arrayStyle = ArrayStyle.GRID)
	public static class NameMapping {
		private final String name;

		private final String mapTo;

		@JsonCreator
		public NameMapping(@JsonProperty("name") final String name, @JsonProperty("mapTo") final String mapTo) {
			this.name = name;
			this.mapTo = mapTo;
		}

		public String getName() {
			return name;
		}

		public String getMapTo() {
			return mapTo;
		}
	}
}
