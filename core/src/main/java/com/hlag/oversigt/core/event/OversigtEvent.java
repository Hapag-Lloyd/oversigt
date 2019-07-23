package com.hlag.oversigt.core.event;

import static java.time.LocalDateTime.now;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hlag.oversigt.util.JsonUtils;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Base Event for all Oversigt events
 *
 * @author avarabyeu
 */
public class OversigtEvent implements Comparable<OversigtEvent> {
	private static final Duration DEFAULT_LIFETIME = Duration.ofHours(1);

	@Nullable
	private String applicationId = null;

	@Nullable
	private String id = null;

	private final long updatedAt = Instant.now().getEpochSecond();

	@Nullable
	private String moreinfo = null;

	private final transient LocalDateTime createdOn = now();

	@Nullable
	private transient Duration lifetime = null;

	/* Do not populate if you don't need dynamic title */
	@Nullable
	private String title;

	public OversigtEvent() {
		// nothing to do
	}

	@Nullable
	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public void setLifetime(final Duration lifetime) {
		this.lifetime = lifetime;
	}

	@Nullable
	Duration getLifetime() {
		return lifetime;
	}

	boolean isValid() {
		return createdOn.plus(lifetime != null ? lifetime : DEFAULT_LIFETIME).isAfter(now());
	}

	void setApplicationId(final String applicationId) {
		this.applicationId = applicationId;
	}

	@Nullable
	String getApplicationId() {
		return applicationId;
	}

	public long getUpdatedAt() {
		return updatedAt;
	}

	@Nullable
	public String getTitle() {
		return title;
	}

	public void setTitle(@Nullable final String title) {
		this.title = title;
	}

	@Nullable
	public String getMoreinfo() {
		return moreinfo;
	}

	public void setMoreinfo(final String moreinfo) {
		this.moreinfo = moreinfo;
	}

	@JsonIgnore
	public LocalDateTime getCreatedOn() {
		return createdOn;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
	}

	@Override
	public boolean equals(@Nullable final Object object) {
		if (!(object instanceof OversigtEvent)) {
			return false;
		}
		return Objects.equals(getId(), ((OversigtEvent) object).getId());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(getId());
	}

	@Override
	public int compareTo(@Nullable final OversigtEvent that) {
		if (that == null) {
			return 1;
		}
		if (id == null && that.id == null) {
			return 0;
		}
		if (id == null) {
			return -1;
		} else if (that.id == null) {
			return 1;
		} else {
			return Objects.requireNonNull(id).compareTo(that.id);
		}
	}

	public String toJson() {
		return JsonUtils.toJson(this);
	}
}
