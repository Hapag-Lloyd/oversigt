package com.hlag.oversigt.sources;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hlag.oversigt.connect.exchange.MailboxInfoRetriever;
import com.hlag.oversigt.connect.exchange.MailboxInfoRetriever.MailboxInfoLoadingProvider;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.sources.event.TwoColumnListEvent;
import com.hlag.oversigt.sources.event.TwoColumnListEvent.ListEventItem;

import microsoft.exchange.webservices.data.core.service.item.Task;

/**
 * @author Constantin Pagenkopp
 */
@EventSource(view = "List", displayName = "Microsoft Exchange Tasks Viewer", hiddenDataItems = { "moreinfo" })
public class ExchangeTasksEventSource extends AbstractExchangeEventSource<TwoColumnListEvent<String>>
		implements MailboxInfoLoadingProvider {

	private enum Style {
		green("color: green;", ""),
		yellow("color: yellow;", ""),
		red("color: red;", "");

		private final String labelStyle;
		private final String valueStyle;

		private Style(String labelStyle, String valueStyle) {
			this.labelStyle = labelStyle;
			this.valueStyle = valueStyle;
		}

		public String getLabelStyle() {
			return labelStyle;
		}

		public String getValueStyle() {
			return valueStyle;
		}
	}

	@Override
	protected void shutDown() throws Exception {
		MailboxInfoRetriever.getInstance().removeProvider(getMailboxName(), null);
		super.shutDown();
	}

	@Override
	protected void startUp() throws Exception {
		MailboxInfoRetriever.getInstance().registerProvider(getMailboxName(), null, this);
		super.startUp();
	}

	@Override
	protected TwoColumnListEvent<String> produceExchangeEvent() throws Exception {
		String mailboxName = getMailboxName();
		List<Task> tasks = MailboxInfoRetriever.getInstance().getTasks(mailboxName);
		if (tasks != null) {
			TwoColumnListEvent<String> event = createEvent(tasks);
			return event;
		} else {
			return null;
		}
	}

	private TwoColumnListEvent<String> createEvent(List<Task> tasks) {
		return new TwoColumnListEvent<>(tasks.stream().map(t -> createItem(t)).collect(Collectors.toList()));
	}

	private ListEventItem<String> createItem(Task task) {
		try {
			String subject = task.getSubject();
			String labelStyle = "";
			String valueStyle = "";
			for (Style style : Style.values()) {
				String searchTerm = "[" + style.name() + "]";
				if (subject.contains(searchTerm)) {
					subject = subject.replace(searchTerm, "");
					labelStyle += style.getLabelStyle();
					valueStyle += style.getValueStyle();
				}
			}

			switch (task.getImportance()) {
				case Low:
					labelStyle += "opacity: 0.75;";
					break;
				case Normal:
					break;
				case High:
					labelStyle += "font-weight: bold;";
					break;
			}

			String createdAtStr = null;
			if (isShowDateColumn()) {
				ZonedDateTime createdAt = task.getDateTimeCreated().toInstant().atZone(getZoneId());
				if (createdAt.toLocalDate().isBefore(LocalDate.now(getZoneId()))) {
					createdAtStr = createdAt.toLocalDate()
							.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY));
				} else {
					createdAtStr = createdAt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY));
				}
			}

			return new ListEventItem<>(subject, createdAtStr, labelStyle, valueStyle);
		} catch (Exception e) {
			throw new RuntimeException("Unable to read task details", e);
		}
	}

	private String mailboxName = "";
	private Duration reloadInterval = Duration.ofMinutes(1);
	private boolean showDateColumn = true;

	@Property(name = "Mailbox Name", description = "The name of the mailbox to inspect")
	public String getMailboxName() {
		return mailboxName;
	}

	public void setMailboxName(String mailboxName) {
		this.mailboxName = mailboxName;
	}

	@Override
	@Property(name = "Reload Interval", description = "How often should the event source query the server to get mailbox information?")
	public Duration getReloadInterval() {
		return reloadInterval;
	}

	public void setReloadInterval(Duration reloadInterval) {
		this.reloadInterval = reloadInterval;
	}

	@Property(name = "Show Date Column", description = "Determines whether the widget should show the date column or not", needsRestart = false)
	public boolean isShowDateColumn() {
		return showDateColumn;
	}

	public void setShowDateColumn(boolean showDateColumn) {
		this.showDateColumn = showDateColumn;
	}
}
