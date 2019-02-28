package com.hlag.oversigt.sources;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hlag.oversigt.connect.exchange.MailboxInfoRetriever;
import com.hlag.oversigt.connect.exchange.MailboxInfoRetriever.Mail;
import com.hlag.oversigt.connect.exchange.MailboxInfoRetriever.MailboxFolder;
import com.hlag.oversigt.connect.exchange.MailboxInfoRetriever.MailboxInfoLoadingProvider;
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
public class ExchangeMailboxEventSource extends AbstractExchangeEventSource<HlBarChartEvent>
		implements MailboxInfoLoadingProvider {

	private static final String UNASSIGNED_LABEL = "NN";
	private static final Color UNASSIGNED_COLOR = Color.Gray;

	@Override
	protected void shutDown() throws Exception {
		MailboxInfoRetriever.getInstance().removeProvider(getMailboxName(), getFolderName());
		super.shutDown();
	}

	@Override
	protected void startUp() throws Exception {
		MailboxInfoRetriever.getInstance().registerProvider(getMailboxName(), getFolderName(), this);
		super.startUp();
	}

	@Override
	protected HlBarChartEvent produceExchangeEvent() throws Exception {
		String mailboxName = getMailboxName();
		String folderName = getFolderName();
		MailboxFolder mailboxFolder = MailboxInfoRetriever.getInstance().getMailbox(mailboxName, folderName);
		if (mailboxFolder == null) {
			return null;
		}
		return createEvent(mailboxFolder.getMails());
	}

	@Override
	protected String getFailureMessage(Exception e) {
		if (e instanceof IllegalArgumentException && getClass() == ExchangeMailboxEventSource.class) {
			return failure(String.format("Unable to read folder %s in mailbox %s", getFolderName(), getMailboxName()));
		} else {
			return null;
		}
	}

	private HlBarChartEvent createEvent(List<Mail> mails) {
		Set<CategoryInfo> categoryInfos = createCategoryInfos();
		for (Mail mail : mails) {
			List<String> categories = mail.getCategories();
			categories.forEach(category -> increaseNumbers(mail, categoryInfos, category));
			if (categories.isEmpty()) {
				increaseNumbers(mail, categoryInfos, null);
			}
		}
		return createEvent(categoryInfos, mails.size());
	}

	private Set<CategoryInfo> createCategoryInfos() {
		Set<CategoryInfo> infos = new LinkedHashSet<>();
		infos.add(new CategoryInfo(getDefaultDisplayOption()));
		for (DisplayOption option : getDisplayOptions()) {
			infos.add(new CategoryInfo(option));
		}
		return infos;
	}

	private HlBarChartEvent createEvent(Set<CategoryInfo> categoryInfos, int noOfMails) {
		List<Category> categories = new ArrayList<>();
		int maxNumberOfMails = Math.max(3, getMaxNumberOfMails(categoryInfos));
		for (CategoryInfo info : categoryInfos) {
			if (info.total > 0 || getShowEmptyCategories()) {
				List<Serie> series = new ArrayList<>();
				Color baseColor = info.option != null ? info.option.getColor() : UNASSIGNED_COLOR;
				Color totalColor = getTotalColor(baseColor);
				series.add(createStrechedSerie(totalColor, info.total, maxNumberOfMails));
				series.add(createStrechedSerie(baseColor, info.total - info.unread, maxNumberOfMails));
				categories.add(new Category(info.total > 0 ? Integer.toString(info.total) : "",
						info.total > 0 ? info.option.getDisplayValue() : "",
						series));
			}
		}
		return new HlBarChartEvent(categories, Integer.toString(noOfMails));
	}

	private Serie createStrechedSerie(Color backgroundColor, int value, int maximum) {
		double height = 0;
		if (value > 0 && maximum > 1) {
			height = getSerieMinimum() + (double) (value - 1) / (maximum - 1) * (1 - getSerieMinimum());
		}
		return new Serie(backgroundColor, height);
	}

	private Color getTotalColor(Color originalColor) {
		return new Color(Math.min(originalColor.getRed() + 10, 255),
				Math.min(originalColor.getGreen() + 10, 255),
				Math.min(originalColor.getBlue() + 10, 255));
	}

	private int getMaxNumberOfMails(Set<CategoryInfo> categoryInfos) {
		int max = 0;
		for (CategoryInfo info : categoryInfos) {
			if (info.total > max) {
				max = info.total;
			}
		}
		return max;
	}

	private void increaseNumbers(Mail mail, Set<CategoryInfo> infos, String categoryName) {
		CategoryInfo info = null;
		if (categoryName != null) {
			try {
				info = infos.stream().filter(i -> i.option.getValue().equals(categoryName)).findAny().get();
			} catch (Exception e) {
				getLogger().warn("Unable to get display option for category: " + categoryName);
			}
		}
		if (info == null) {
			info = infos.stream().filter(i -> i.option == getDefaultDisplayOption()).findAny().get();
		}

		info.total++;
		if (!mail.isRead()) {
			info.unread++;
		}
	}

	private static class CategoryInfo {

		private final DisplayOption option;
		private int total = 0;
		private int unread = 0;

		public CategoryInfo(DisplayOption option) {
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
		public boolean equals(Object that) {
			if (this == that) {
				return true;
			}
			if (that == null) {
				return false;
			}
			if (this.getClass() != that.getClass()) {
				return false;
			}
			CategoryInfo thatOther = (CategoryInfo) that;
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

	private Duration reloadInterval = Duration.ofMinutes(1);
	private double serieMinimum = 0.35;
	private String mailboxName = "";
	private String folderName = WellKnownFolderName.Inbox.name();

	private boolean showEmptyCategories = false;
	private DisplayOption[] displayOptions = null;
	private DisplayOption defaultDisplayOption = new DisplayOption(UNASSIGNED_LABEL, UNASSIGNED_COLOR);

	@Property(name = "Mailbox Name", description = "The name of the mailbox to inspect")
	public String getMailboxName() {
		return mailboxName;
	}

	public void setMailboxName(String mailboxName) {
		this.mailboxName = mailboxName;
	}

	@Property(name = "Folder Name", description = "The folder to be examined be this event source")
	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(String folderName) {
		this.folderName = folderName;
	}

	// TODO this property should be a property of the Widget, not of the EventSource. The EventSource needs to differentiate the categories, but it should be up to the Widget, how the categories are displayed
	@Property(name = "Display Options", description = "Optional mapping of original display values to originated display options, such as value and color.")
	public DisplayOption[] getDisplayOptions() {
		if (displayOptions == null) {
			return new DisplayOption[0];
		}
		return displayOptions;
	}

	public void setDisplayOptions(DisplayOption[] displayOptions) {
		this.displayOptions = displayOptions;
	}

	@Property(name = "Default Display Option", description = "Optionally all unmapped display values can be displayed by this value and color.")
	public DisplayOption getDefaultDisplayOption() {
		return defaultDisplayOption;
	}

	public void setDefaultDisplayOption(DisplayOption defaultDisplayOption) {
		this.defaultDisplayOption = defaultDisplayOption;
	}

	// TODO this property should be a property of the Widget, not of the EventSource
	@Property(name = "Show Empty Categories", description = "If enabled the event of this event source will contain all categories, even those containing any data. Otherwise they will be excluded.")
	public boolean getShowEmptyCategories() {
		return showEmptyCategories;
	}

	public void setShowEmptyCategories(boolean showEmptyCategories) {
		this.showEmptyCategories = showEmptyCategories;
	}

	// TODO this property should be a property of the Widget, not of the EventSource
	@Property(name = "Series Minimum", description = "The minimum percentage height of the series. A bar will never get below this height.")
	public double getSerieMinimum() {
		return serieMinimum;
	}

	public void setSerieMinimum(double serieMinimum) {
		this.serieMinimum = serieMinimum;
	}

	@Override
	@Property(name = "Reload Interval", description = "How often should the event source query the server to get mailbox information?")
	public Duration getReloadInterval() {
		return reloadInterval;
	}

	public void setReloadInterval(Duration reloadInterval) {
		this.reloadInterval = reloadInterval;
	}
}
