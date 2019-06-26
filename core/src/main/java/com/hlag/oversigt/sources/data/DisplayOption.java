package com.hlag.oversigt.sources.data;

import java.util.Objects;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import com.hlag.oversigt.properties.Color;
import com.hlag.oversigt.properties.JsonBasedData;
import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

import edu.umd.cs.findbugs.annotations.Nullable;

@JsonHint(headerTemplate = "{{ self.displayValue }}", arrayStyle = ArrayStyle.TABS)
public class DisplayOption implements Comparable<DisplayOption>, JsonBasedData {
	@NotNull
	private Color color = Color.GRAY;

	@NotNull
	private String displayValue = "";

	@NotNull
	private String value = "";

	public DisplayOption() {}

	public DisplayOption(final String displayValue) {
		this.displayValue = Objects.requireNonNull(displayValue);
	}

	public DisplayOption(final String displayValue, final Color color) {
		this(displayValue);
		this.color = Objects.requireNonNull(color);
	}

	@Override
	public int compareTo(@Nullable final DisplayOption o) {
		return getDisplayValue().compareTo(Optional.ofNullable(o).map(DisplayOption::getDisplayValue).orElse(""));
	}

	@Override
	public boolean equals(@Nullable final Object o) {
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
		this.color = Objects.requireNonNull(color);
	}

	public String getDisplayValue() {
		return displayValue;
	}

	public String formatDisplayValue(final int count) {
		return String.format(getDisplayValue(), count);
	}

	public void setDisplayValue(final String displayValue) {
		this.displayValue = Objects.requireNonNull(displayValue);
	}

	public String getValue() {
		return value;
	}

	public void setValue(final String value) {
		this.value = Objects.requireNonNull(value);
	}
}
