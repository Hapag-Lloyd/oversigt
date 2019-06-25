package com.hlag.oversigt.sources;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.sources.event.TwoColumnListEvent;
import com.hlag.oversigt.sources.event.TwoColumnListEvent.ListEventItem;

import microsoft.exchange.webservices.data.core.service.item.Task;

/**
 * @author Constantin Pagenkopp
 */
@EventSource(view = "List", displayName = "Microsoft Exchange Tasks Viewer", hiddenDataItems = { "moreinfo" })
public class ExchangeTasksEventSource extends AbstractExchangeEventSource<TwoColumnListEvent<String>> {

	private boolean showDateColumn = true;

	@Property(name = "Show Date Column",
			description = "Determines whether the widget should show the date column or not")
	public boolean isShowDateColumn() {
		return showDateColumn;
	}

	public void setShowDateColumn(final boolean showDateColumn) {
		this.showDateColumn = showDateColumn;
	}

	@Override
	protected Optional<TwoColumnListEvent<String>> produceExchangeEvent() throws Exception {
		final List<Task> tasks = getExchangeClient().loadTasks();
		final TwoColumnListEvent<String> event = createEvent(tasks);
		return Optional.of(event);
	}

	private TwoColumnListEvent<String> createEvent(final List<Task> tasks) {
		return new TwoColumnListEvent<>(tasks.stream().map(t -> createItem(t)).collect(Collectors.toList()));
	}

	private ListEventItem<String> createItem(final Task task) {
		try {
			String subject = task.getSubject();
			String labelStyle = "";
			String valueStyle = "";
			for (final Style style : Style.values()) {
				final String searchTerm = "[" + style.name() + "]";
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
			case High:
				labelStyle += "font-weight: bold;";
				break;
			case Normal:
			default:
				break;
			}

			String createdAtStr = null;
			if (isShowDateColumn()) {
				final ZonedDateTime createdAt = task.getDateTimeCreated().toInstant().atZone(getZoneId());
				if (createdAt.toLocalDate().isBefore(LocalDate.now(getZoneId()))) {
					createdAtStr
							= createdAt.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY));
				} else {
					createdAtStr = createdAt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY));
				}
			}

			return new ListEventItem<>(subject, createdAtStr, labelStyle, valueStyle);
		} catch (final Exception e) {
			throw new RuntimeException("Unable to read task details", e);
		}
	}

	private enum Style {
		green("color: green;", ""),
		yellow("color: yellow;", ""),
		red("color: red;", "");

		private final String labelStyle;

		private final String valueStyle;

		Style(final String labelStyle, final String valueStyle) {
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
}
