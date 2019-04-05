package com.hlag.oversigt.core.event;

import static java.time.LocalDateTime.now;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Base Event for all Oversigt events
 *
 * @author avarabyeu
 */
public class OversigtEvent implements Comparable<OversigtEvent> {
	private static final Duration DEFAULT_LIFETIME = Duration.ofHours(1);

	private String applicationId;

	private String id;

	private long updatedAt;

	private String moreinfo = null;

	private final transient LocalDateTime internal_createdOn = now();

	private transient Duration lifetime = null;

	/* Do not populate if you don't need dynamic title */
	private String title;

	public OversigtEvent() {
		updatedAt = Instant.now().getEpochSecond();
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public void setLifetime(final Duration lifetime) {
		this.lifetime = lifetime;
	}

	Duration getLifetime() {
		return lifetime;
	}

	boolean isValid() {
		return internal_createdOn.plus(lifetime != null ? lifetime : DEFAULT_LIFETIME).isAfter(now());
	}

	void setApplicationId(final String applicationId) {
		this.applicationId = applicationId;
	}

	String getApplicationId() {
		return applicationId;
	}

	public long getUpdatedAt() {
		return updatedAt;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(final String title) {
		this.title = title;
	}

	public String getMoreinfo() {
		return moreinfo;
	}

	public void setMoreinfo(final String moreinfo) {
		this.moreinfo = moreinfo;
	}

	@JsonIgnore
	public LocalDateTime getCreatedOn() {
		return internal_createdOn;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
	}

	@Override
	public int compareTo(final OversigtEvent that) {
		if (id == null && that.id == null) {
			return 0;
		} else if (id == null) {
			return -1;
		} else if (that.id == null) {
			return 1;
		} else {
			return getId().compareTo(that.getId());
		}
	}
}
