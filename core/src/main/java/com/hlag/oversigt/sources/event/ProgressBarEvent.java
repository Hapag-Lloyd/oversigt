package com.hlag.oversigt.sources.event;

import java.util.Collection;

import com.hlag.oversigt.core.event.OversigtEvent;

public class ProgressBarEvent extends OversigtEvent {
	private Collection<Item> progress_items;

	public ProgressBarEvent(final String title, final Collection<Item> items) {
		setTitle(title);
		progress_items = items;
	}

	public ProgressBarEvent(final Collection<Item> items) {
		this(null, items);
	}

	public Collection<Item> getItems() {
		return progress_items;
	}

	public void setItems(final Collection<Item> items) {
		progress_items = items;
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
