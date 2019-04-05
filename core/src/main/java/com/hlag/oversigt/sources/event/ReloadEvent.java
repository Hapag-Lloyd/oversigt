package com.hlag.oversigt.sources.event;

import java.util.Arrays;
import java.util.Collection;

import com.hlag.oversigt.core.event.NoCache;
import com.hlag.oversigt.core.event.OversigtEvent;

@NoCache
public class ReloadEvent extends OversigtEvent {
	private Collection<String> dashboards;

	public ReloadEvent(final String... dashboards) {
		this(Arrays.asList(dashboards));
	}

	public ReloadEvent(final Collection<String> dashboards) {
		setId("reload");
		this.dashboards = dashboards.isEmpty() ? null : dashboards;
	}

	public Collection<String> getDashboards() {
		return dashboards;
	}

	public void setDashboards(final Collection<String> dashboards) {
		this.dashboards = dashboards;
	}
}
