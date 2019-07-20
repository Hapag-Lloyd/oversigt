package com.hlag.oversigt.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hlag.oversigt.properties.Color;

import edu.umd.cs.findbugs.annotations.Nullable;

public class Widget implements Comparable<Widget> {
	private static final Comparator<Widget> COMPARE_BY_NAME
			= (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());

	private static final Comparator<Widget> COMPARE_BY_ID = (a, b) -> Integer.compare(a.getId(), b.getId());

	private static final Comparator<Widget> COMPARE = COMPARE_BY_NAME.thenComparing(COMPARE_BY_ID);

	@JsonIgnore
	private final EventSourceInstance eventSourceInstance;

	@NotNull
	private int id = -1;

	@NotBlank
	private String title;

	@NotBlank
	private String name;

	@NotNull
	private boolean enabled = false;

	@NotNull
	private int posX = 1;

	@NotNull
	private int posY = 1;

	@NotNull
	@Min(1)
	private int sizeX = 3;

	@NotNull
	@Min(1)
	private int sizeY = 3;

	@NotNull
	private Color backgroundColor = Color.random();

	private String style = "";

	private final Map<EventSourceProperty, String> data = new HashMap<>();

	public Widget(final EventSourceInstance eventSourceInstance) {
		this.eventSourceInstance = eventSourceInstance;
		name = eventSourceInstance.getName();
		title = eventSourceInstance.getName();
	}

	public Widget(@NotNull final int id,
			final EventSourceInstance eventSource,
			@NotBlank final String title,
			@NotBlank final String name,
			@NotNull final boolean enabled,
			@NotNull final int posX,
			@NotNull final int posY,
			@NotNull @Min(1) final int sizeX,
			@NotNull @Min(1) final int sizeY,
			@NotNull final Color backgroundColor,
			final String style) {
		this.id = id;
		eventSourceInstance = eventSource;
		this.title = title;
		this.name = name;
		this.enabled = enabled;
		this.posX = posX;
		this.posY = posY;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.backgroundColor = backgroundColor;
		this.style = style;
	}

	public int getId() {
		return id;
	}

	public void setId(final int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public String getTitle() {
		return title;
	}

	@JsonIgnore
	public String getView() {
		return eventSourceInstance.getDescriptor().getView();
	}

	@JsonIgnore
	public String getType() {
		return eventSourceInstance.getDescriptor().getServiceClass().map(Class::getSimpleName).orElse("");
	}

	public EventSourceInstance getEventSourceInstance() {
		return eventSourceInstance;
	}

	public int getPosX() {
		return posX;
	}

	public int getPosY() {
		return posY;
	}

	public int getSizeX() {
		return sizeX;
	}

	public int getSizeY() {
		return sizeY;
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public String getStyle() {
		return style;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setPosX(final int posX) {
		this.posX = posX;
	}

	public void setPosY(final int posY) {
		this.posY = posY;
	}

	public void setSizeX(final int sizeX) {
		this.sizeX = sizeX;
	}

	public void setSizeY(final int sizeY) {
		this.sizeY = sizeY;
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	public void setBackgroundColor(final Color backgroundColor) {
		this.backgroundColor = backgroundColor;
	}

	public void setStyle(final String style) {
		this.style = style;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public void setTitle(final String title) {
		this.title = title;
	}

	@Nullable
	public String getWidgetData(final EventSourceProperty property) {
		return data.get(property);
	}

	public void setWidgetData(final EventSourceProperty property, final String value) {
		if (!property.isCustomValuesAllowed()
				&& !property.getAllowedValues().isEmpty()
				&& !property.getAllowedValues().contains(value)) {
			throw new RuntimeException("The value '" + value + "' is not allowed for property " + property.getName());
		}
		data.put(property, value);
	}

	public boolean hasWidgetData(final EventSourceProperty property) {
		return data.containsKey(property);
	}

	public void removeWidgetData(final EventSourceProperty property) {
		data.remove(property);
	}

	public String getWidgetDataForDashboard(final EventSourceProperty property) {
		return getEventSourceInstance().hasPropertyValue(property)
				? getEventSourceInstance().getPropertyValueString(property)
				: data.get(property);
	}

	public boolean hasWidgetDataForDashboard(final EventSourceProperty property) {
		return getEventSourceInstance().hasPropertyValue(property) || data.containsKey(property);
	}

	@Override
	public int compareTo(@Nullable final Widget that) {
		return COMPARE.compare(this, that);
	}

	/**
	 * Returns the widgets css class attribute merged with class information based
	 * on other attributes.
	 *
	 * @return css classes
	 * @deprecated use {@link DashboardDesign#getDisplayClass(Widget)} instead
	 */
	@Deprecated
	@JsonIgnore
	public String getDisplayClass() {
		// this method is used by the Dashboard template
		return DashboardDesignHelper.getDisplayClass(this);
	}

	/**
	 * Returns the widgets css style attribute merged with style information based
	 * on other attributes.
	 *
	 * @return css styles
	 * @deprecated use {@link DashboardDesign#getDisplayStyle(Widget)} instead
	 */
	@Deprecated
	@JsonIgnore
	public String getDisplayStyle() {
		// this method is used by the Dashboard template
		return DashboardDesignHelper.getDisplayStyle(this);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
	}
}
