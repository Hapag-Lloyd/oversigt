package com.hlag.oversigt.sources;

import java.util.Optional;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.sources.event.NumberEvent;

@EventSource(displayName = "GIT Commit Count", view = "Number")
public class GitCommitCountEventSource extends AbstractGitCommitEventSource<OversigtEvent> {
	private long lastCommitCount = 0;

	public GitCommitCountEventSource() {
		// no fields to be initialized
	}

	@Override
	protected Optional<OversigtEvent> produceEvent() throws Exception {
		final long commitCount = streamLog(s -> s.count());
		final NumberEvent event = new NumberEvent((int) commitCount, (int) lastCommitCount);
		lastCommitCount = commitCount;
		return Optional.of(event);
	}
}
