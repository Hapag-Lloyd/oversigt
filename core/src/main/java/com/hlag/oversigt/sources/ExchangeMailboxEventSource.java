package com.hlag.oversigt.sources;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hlag.oversigt.connect.exchange.Mail;
import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.core.eventsource.Property;
import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.sources.data.DisplayOption;
import com.hlag.oversigt.sources.event.HlBarChartEvent;
import com.hlag.oversigt.sources.event.HlBarChartEvent.Category;
import com.hlag.oversigt.sources.event.HlBarChartEvent.Serie;

import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;

/**
 * @author Constantin Pagenkopp
 */
@EventSource(view = "HlBarChart", displayName = "Microsoft Exchange Mailbox Viewer")
public class ExchangeMailboxEventSource extends AbstractExchangeEventSource<HlBarChartEvent> {

	private static final String UNASSIGNED_LABEL = "NN";

	private static final Color UNASSIGNED_COLOR = Color.Gray;

	private double serieMinimum = 0.35;

	private String folderName = WellKnownFolderName.Inbox.name();

	private boolean showEmptyCategories = false;

	private DisplayOption[] displayOptions = null;

	private DisplayOption defaultDisplayOption = new DisplayOption(UNASSIGNED_LABEL, UNASSIGNED_COLOR);

	@Property(name = "Folder Name", description = "The folder to be examined be this event source")
	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(final String folderName) {
		this.folderName = folderName;
	}

	// TODO this property should be a property of the Widget, not of the
	// EventSource. The EventSource needs to differentiate the categories, but it
	// should be up to the Widget, how the categories are displayed
	@Property(name = "Display Options",
			description = "Optional mapping of original display values to originated display options, such as value and color.")
	public DisplayOption[] getDisplayOptions() {
		if (displayOptions == null) {
			return new DisplayOption[0];
		}
		return displayOptions;
	}

	public void setDisplayOptions(final DisplayOption[] displayOptions) {
		this.displayOptions = displayOptions;
	}

	@Property(name = "Default Display Option",
			description = "Optionally all unmapped display values can be displayed by this value and color.")
	public DisplayOption getDefaultDisplayOption() {
		return defaultDisplayOption;
	}

	public void setDefaultDisplayOption(final DisplayOption defaultDisplayOption) {
		this.defaultDisplayOption = defaultDisplayOption;
	}

	// TODO this property should be a property of the Widget, not of the EventSource
	@Property(name = "Show Empty Categories",
			description = "If enabled the event of this event source will contain all categories, even those containing any data. Otherwise they will be excluded.")
	public boolean getShowEmptyCategories() {
		return showEmptyCategories;
	}

	public void setShowEmptyCategories(final boolean showEmptyCategories) {
		this.showEmptyCategories = showEmptyCategories;
	}

	// TODO this property should be a property of the Widget, not of the EventSource
	@Property(name = "Series Minimum",
			description = "The minimum percentage height of the series. A bar will never get below this height.")
	public double getSerieMinimum() {
		return serieMinimum;
	}

	public void setSerieMinimum(final double serieMinimum) {
		this.serieMinimum = serieMinimum;
	}

	@Override
	protected HlBarChartEvent produceExchangeEvent() throws Exception {
		final List<Mail> mails = getExchangeClient().loadMails(getFolderName());
		return createEvent(mails);
	}

	@Override
	protected String getFailureMessage(final Exception e) {
		if (e instanceof IllegalArgumentException && getClass() == ExchangeMailboxEventSource.class) {
			return failure(String
					.format("Unable to read folder %s for user %s", getFolderName(), getCredentials().getUsername()));
		}
		return null;
	}

	private HlBarChartEvent createEvent(final List<Mail> mails) {
		final Set<CategoryInfo> categoryInfos = createCategoryInfos();
		for (final Mail mail : mails) {
			final List<String> categories = mail.getCategories();
			categories.forEach(category -> increaseNumbers(mail, categoryInfos, category));
			if (categories.isEmpty()) {
				increaseNumbers(mail, categoryInfos, null);
			}
		}
		return createEvent(categoryInfos, mails.size());
	}

	private HlBarChartEvent createEvent(final Set<CategoryInfo> categoryInfos, final int noOfMails) {
		final List<Category> categories = new ArrayList<>();
		final int maxNumberOfMails = Math.max(3, getMaxNumberOfMails(categoryInfos));
		for (final CategoryInfo info : categoryInfos) {
			if (info.total > 0 || getShowEmptyCategories()) {
				final List<Serie> series = new ArrayList<>();
				final Color baseColor = info.option != null ? info.option.getColor() : UNASSIGNED_COLOR;
				final Color totalColor = getTotalColor(baseColor);
				series.add(createStrechedSerie(totalColor, info.total, maxNumberOfMails));
				series.add(createStrechedSerie(baseColor, info.total - info.unread, maxNumberOfMails));
				categories.add(new Category(info.total > 0 ? Integer.toString(info.total) : "",
						info.total > 0 ? info.option.getDisplayValue() : "",
						series));
			}
		}
		return new HlBarChartEvent(categories, Integer.toString(noOfMails));
	}

	private Set<CategoryInfo> createCategoryInfos() {
		final Set<CategoryInfo> infos = new LinkedHashSet<>();
		infos.add(new CategoryInfo(getDefaultDisplayOption()));
		for (final DisplayOption option : getDisplayOptions()) {
			infos.add(new CategoryInfo(option));
		}
		return infos;
	}

	private Serie createStrechedSerie(final Color backgroundColor, final int value, final int maximum) {
		double height = 0;
		if (value > 0 && maximum > 1) {
			height = getSerieMinimum() + (double) (value - 1) / (maximum - 1) * (1 - getSerieMinimum());
		}
		return new Serie(backgroundColor, height);
	}

	private Color getTotalColor(final Color originalColor) {
		return new Color(Math.min(originalColor.getRed() + 10, 255),
				Math.min(originalColor.getGreen() + 10, 255),
				Math.min(originalColor.getBlue() + 10, 255));
	}

	private int getMaxNumberOfMails(final Set<CategoryInfo> categoryInfos) {
		int max = 0;
		for (final CategoryInfo info : categoryInfos) {
			if (info.total > max) {
				max = info.total;
			}
		}
		return max;
	}

	private void increaseNumbers(final Mail mail, final Set<CategoryInfo> infos, final String categoryName) {
		CategoryInfo info = null;
		if (categoryName != null) {
			try {
				info = infos.stream().filter(i -> i.option.getValue().equals(categoryName)).findAny().get();
			} catch (final Exception e) {
				getLogger().warn("Unable to get display option for category: " + categoryName, e);
			}
		}
		if (info == null) {
			info = infos.stream().filter(i -> i.option == getDefaultDisplayOption()).findAny().get();
		}

		info.total += 1;
		if (!mail.isRead()) {
			info.unread += 1;
		}
	}

	private static class CategoryInfo {

		private final DisplayOption option;

		private int total = 0;

		private int unread = 0;

		CategoryInfo(final DisplayOption option) {
			this.option = Objects.requireNonNull(option);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (option.getDisplayValue() == null ? 0 : option.getDisplayValue().hashCode());
			return result;
		}

		@Override
		public boolean equals(final Object that) {
			if (this == that) {
				return true;
			}
			if (that == null) {
				return false;
			}
			if (this.getClass() != that.getClass()) {
				return false;
			}
			final CategoryInfo thatOther = (CategoryInfo) that;
			if (option.getDisplayValue() == null) {
				if (thatOther.option.getDisplayValue() != null) {
					return false;
				}
			} else if (!option.getDisplayValue().equals(thatOther.option.getDisplayValue())) {
				return false;
			}
			return true;
		}
	}
}
