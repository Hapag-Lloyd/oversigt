package com.hlag.oversigt.sources.data;

import java.time.LocalDate;

import javax.validation.constraints.NotNull;

import com.hlag.oversigt.sources.data.JsonHint.ArrayStyle;

@JsonHint(headerTemplate = "{{ self.name }}", arrayStyle = ArrayStyle.TABLE)
public class Birthday {
	@NotNull
	private String name;

	@NotNull
	private LocalDate date;

	public Birthday() {
		// no fields to be initialized
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(final LocalDate date) {
		this.date = date;
	}
}
