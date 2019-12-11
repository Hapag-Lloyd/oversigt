package com.hlag.oversigt.sources.event;

import java.util.Collection;

import com.hlag.oversigt.core.event.OversigtEvent;

import edu.umd.cs.findbugs.annotations.Nullable;

public class ProgressBarEvent extends OversigtEvent {
	private Collection<Item> progressItems;

	public ProgressBarEvent(@Nullable final String title, final Collection<Item> items) {
		setTitle(title);
		progressItems = items;
	}

	public ProgressBarEvent(final Collection<Item> items) {
		this(null, items);
	}

	public Collection<Item> getItems() {
		return progressItems;
	}

	public void setItems(final Collection<Item> items) {
		progressItems = items;
	}

	public static class Item {
		private String name;

		private byte progress;

		public Item(final String name, final byte progress) {
			this.name = name;
			this.progress = progress;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		public byte getProgress() {
			return progress;
		}

		public void setProgress(final byte progress) {
			this.progress = progress;
		}
	}
}
