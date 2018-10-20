package com.hlag.oversigt.sources;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.sources.data.DisplayOption;
import com.hlag.oversigt.sources.event.PieChartEvent;

@EventSource(view = "PieChart", displayName = "Jira Tickets (as Pie Chart)")
public class JiraPieChartEventSource extends AbstractJiraEventSource<PieChartEvent> {
	@Override
	protected PieChartEvent produceEvent() {
		Map<DisplayOption, Set<Issue>> issues = getJiraTickets();

		if (issues != null) {
			long sumMails = issues.values().stream().flatMap(Set::stream).count();

			PieChartEvent event = new PieChartEvent();
			for (Entry<DisplayOption, Set<Issue>> entry : issues.entrySet()) {
				DisplayOption displayOption = entry.getKey();
				int count = entry.getValue().size();

				event.addData(displayOption.formatDisplayValue(count),
						(double) count / sumMails,
						displayOption.getColor().getHexColor());
			}
			return event;
		} else {
			return null;
		}
	}
}
