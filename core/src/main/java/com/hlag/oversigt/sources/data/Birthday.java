package com.hlag.oversigt.sources.data;

import java.time.LocalDate;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

@JsonHint(headerTemplate = "{{ self.name }}", arrayStyle = ArrayStyle.TABLE)
public class Birthday {
	@NotNull
	private String name = "";

	@NotNull
	private LocalDate date = LocalDate.now().minusYears(20);

	public Birthday() {
		// leave default values
	}

	public Birthday(@NotNull final String name, @NotNull final LocalDate date) {
		this.name = Objects.requireNonNull(name);
		this.date = Objects.requireNonNull(date);
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = Objects.requireNonNull(name);
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(final LocalDate date) {
		this.date = Objects.requireNonNull(date);
	}
}
