package com.hlag.oversigt.sources.event;

import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.hlag.oversigt.sources.event.TwoColumnListEvent.ListEventItem;

public class TwoColumnListEvent<T> extends ListEvent<ListEventItem<T>> {
	public TwoColumnListEvent(final List<? extends ListEventItem<T>> items) {
		super(items);
	}

	public static class ListEventItem<T> {
		private String label;

		private T value;

		private String labelStyle;

		private String valueStyle;

		public ListEventItem(final String label, final T value, final String labelStyle, final String valueStyle) {
			this.label = label;
			this.value = value;
			this.labelStyle = labelStyle;
			this.valueStyle = valueStyle;
		}

		public ListEventItem(final String label, final T value) {
			this(label, value, "", "");
		}

		public ListEventItem(final Entry<? extends CharSequence, T> entry) {
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

		public void setLabelStyle(final String labelStyle) {
			this.labelStyle = labelStyle;
		}

		public void setValueStyle(final String valueStyle) {
			this.valueStyle = valueStyle;
		}

		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
		}
	}
}
