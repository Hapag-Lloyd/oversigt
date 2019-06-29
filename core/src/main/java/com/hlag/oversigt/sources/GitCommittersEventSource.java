package com.hlag.oversigt.sources;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.sources.event.TwoColumnListEvent;
import com.hlag.oversigt.sources.event.TwoColumnListEvent.ListEventItem;

@EventSource(displayName = "GIT Committers", view = "List", hiddenDataItems = { "moreinfo", "updated-at-message" })
public class GitCommittersEventSource extends AbstractGitCommitEventSource<OversigtEvent> {
	public GitCommittersEventSource() {
		// no fields to be initialized
	}

	@Override
	protected OversigtEvent produceEvent() throws Exception {
		// Count commits per user
		final Map<String, Integer> committers
				= streamLogWithoutFilteredUsers(s -> s.map(r -> r.getCommitterIdent().getName())
						.map(createNameMapper())
						.collect(toMap(Function.identity(), x -> 1, (x, y) -> x + y)));

		// Order by commit count
		final List<ListEventItem<Integer>> list = committers.entrySet()
				.stream()
				.sorted((a, b) -> -Integer.compare(a.getValue(), b.getValue()))
				.limit(getLimit() > 0 ? getLimit() : committers.size())
				.map(ListEventItem::new)
				.collect(toList());

		// create event
		return new TwoColumnListEvent<>(list);
	}
}
