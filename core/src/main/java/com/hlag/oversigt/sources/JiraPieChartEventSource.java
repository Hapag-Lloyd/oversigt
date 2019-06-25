package com.hlag.oversigt.sources;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.sources.data.DisplayOption;
import com.hlag.oversigt.sources.event.PieChartEvent;

@EventSource(view = "PieChart", displayName = "Jira Tickets (as Pie Chart)")
public class JiraPieChartEventSource extends AbstractJiraEventSource<PieChartEvent> {
	@Override
	protected Optional<PieChartEvent> produceEvent() {
		final Map<DisplayOption, Set<Issue>> issues = getJiraTickets();
		if (issues == null) {
			return Optional.empty();
		}

		final long sumMails = issues.values().stream().flatMap(Set::stream).count();

		final PieChartEvent event = new PieChartEvent();
		for (final Entry<DisplayOption, Set<Issue>> entry : issues.entrySet()) {
			final DisplayOption displayOption = entry.getKey();
			final int count = entry.getValue().size();

			event.addData(displayOption.formatDisplayValue(count),
					(double) count / sumMails,
					displayOption.getColor().getHexColor());
		}
		return Optional.of(event);
	}
}
