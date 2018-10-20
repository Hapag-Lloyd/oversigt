package com.hlag.oversigt.sources;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.sources.event.NumberEvent;

@EventSource(displayName = "GIT Commit Count", view = "Number")
public class GitCommitCountEventSource extends AbstractGitCommitEventSource<OversigtEvent> {
	private long lastCommitCount = 0;

	@Override
	protected OversigtEvent produceEvent() throws Exception {
		long commitCount = streamLog(s -> s.count());
		NumberEvent event = new NumberEvent((int) commitCount, (int) lastCommitCount);
		lastCommitCount = commitCount;
		return event;
	}
}
