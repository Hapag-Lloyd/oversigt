package com.hlag.oversigt.sources;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.EventSourceException;
import com.hlag.oversigt.sources.event.TwoColumnListEvent;
import com.hlag.oversigt.sources.event.TwoColumnListEvent.ListEventItem;

@EventSource(displayName = "GIT Committers", view = "List", hiddenDataItems = { "moreinfo", "updated-at-message" })
public class GitCommittersEventSource extends AbstractGitCommitEventSource<OversigtEvent> {
	public GitCommittersEventSource() {
		// no fields to be initialized
	}

	@Override
	protected Optional<OversigtEvent> produceEvent() throws EventSourceException {
		// Count commits per user
		final Map<String, Integer> committers;
		try {
			committers = streamLogWithoutFilteredUsers(s -> s.map(r -> r.getCommitterIdent().getName())
					.map(createNameMapper())
					.collect(toMap(Function.identity(), x -> 1, (x, y) -> x + y)));
		} catch (final GitAPIException | IOException e) {
			throw new EventSourceException("Unable to retreive GIT information", e);
		}

		// Order by commit count
		final List<ListEventItem<Integer>> list = committers.entrySet()
				.stream()
				.sorted((a, b) -> -Integer.compare(a.getValue(), b.getValue()))
				.limit(getLimit() > 0 ? getLimit() : committers.size())
				.map(ListEventItem::new)
				.collect(toList());

		// create event
		return Optional.of(new TwoColumnListEvent<>(list));
	}
}
