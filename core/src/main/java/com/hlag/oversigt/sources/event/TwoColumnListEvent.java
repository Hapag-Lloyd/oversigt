package com.hlag.oversigt.sources.event;

import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.hlag.oversigt.sources.event.TwoColumnListEvent.ListEventItem;

public class TwoColumnListEvent<T> extends ListEvent<ListEventItem<T>> {
	public TwoColumnListEvent(List<ListEventItem<T>> items) {
		super(items);
	}

	public static class ListEventItem<T> {
		private String label;
		private T value;
		private String labelStyle;
		private String valueStyle;

		public ListEventItem(String label, T value, String labelStyle, String valueStyle) {
			this.label = label;
			this.value = value;
			this.labelStyle = labelStyle;
			this.valueStyle = valueStyle;
		}

		public ListEventItem(String label, T value) {
			this(label, value, "", "");
		}

		public ListEventItem(Entry<? extends CharSequence, T> entry) {
			this(entry.getKey().toString(), entry.getValue());
		}

		public String getLabel() {
			return label;
		}

		public T getValue() {
			return value;
		}

		public String getLabelStyle() {
			return labelStyle;
		}

		public String getValueStyle() {
			return valueStyle;
		}

		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
		}
	}
}
