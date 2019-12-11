package com.hlag.oversigt.sources;

import java.io.IOException;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.EventSourceException;
import com.hlag.oversigt.sources.event.NumberEvent;

@EventSource(displayName = "GIT Commit Count", view = "Number")
public class GitCommitCountEventSource extends AbstractGitCommitEventSource<OversigtEvent> {
	private long lastCommitCount = 0;

	public GitCommitCountEventSource() {
		// no fields to be initialized
	}

	@Override
	protected Optional<OversigtEvent> produceEvent() throws EventSourceException {
		final long commitCount;
		try {
			commitCount = streamLog(s -> s.count());
		} catch (final GitAPIException | IOException e) {
			throw new EventSourceException("Unable to retreive GIT information", e);
		}
		final NumberEvent event = new NumberEvent((int) commitCount, (int) lastCommitCount);
		lastCommitCount = commitCount;
		return Optional.of(event);
	}
}
