package com.hlag.oversigt.sources.data;

import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

@JsonHint(headerTemplate = "{{ self.displayValue }}", arrayStyle = ArrayStyle.TABS)
public class DisplayOption implements Comparable<DisplayOption>, JsonBasedData {
	@NotNull
	private Color color = Color.GRAY;

	@NotNull
	private String displayValue = null;

	@NotNull
	private String value = null;

	public DisplayOption() {}

	public DisplayOption(final String displayValue) {
		this.displayValue = Objects.requireNonNull(displayValue);
	}

	public DisplayOption(final String displayValue, final Color color) {
		this(displayValue);
		this.color = color;
	}

	@Override
	public int compareTo(final DisplayOption o) {
		return getDisplayValue().compareTo(o.getDisplayValue());
	}

	@Override
	public boolean equals(final Object o) {
		return o != null && o instanceof DisplayOption && compareTo((DisplayOption) o) == 0;
	}

	@Override
	public int hashCode() {
		return displayValue.hashCode();
	}

	public Color getColor() {
		return color;
	}

	public void setColor(final Color color) {
		this.color = color;
	}

	public String getDisplayValue() {
		return displayValue;
	}

	public String formatDisplayValue(final int count) {
		return String.format(getDisplayValue(), count);
	}

	public void setDisplayValue(final String displayValue) {
		this.displayValue = displayValue;
	}

	public String getValue() {
		return value;
	}

	public void setValue(final String value) {
		this.value = value;
	}
}
