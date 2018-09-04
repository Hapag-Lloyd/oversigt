package com.hlag.oversigt.sources.event;

import java.util.Collection;

import com.hlag.oversigt.core.OversigtEvent;

public class ProgressBarEvent extends OversigtEvent {
	private Collection<Item> progress_items;

	public ProgressBarEvent(String title, Collection<Item> items) {
		setTitle(title);
		this.progress_items = items;
	}

	public ProgressBarEvent(Collection<Item> items) {
		this(null, items);
	}

	public Collection<Item> getItems() {
		return this.progress_items;
	}

	public void setItems(Collection<Item> items) {
		this.progress_items = items;
	}

	public static class Item {
		private String name;
		private byte progress;

		public Item(String name, byte progress) {
			this.name = name;
			this.progress = progress;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public byte getProgress() {
			return this.progress;
		}

		public void setProgress(byte progress) {
			this.progress = progress;
		}
	}
}