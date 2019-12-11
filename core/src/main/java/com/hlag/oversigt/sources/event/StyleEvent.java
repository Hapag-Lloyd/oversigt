package com.hlag.oversigt.sources.event;

import static com.hlag.oversigt.util.HttpUtils.encodeByteArrayToUrlString;

import com.hlag.oversigt.core.event.OversigtEvent;

import de.larssh.utils.Nullables;

public class StyleEvent extends OversigtEvent {
	public static StyleEvent backgroundImage(final String url) {
		return new StyleEvent("background-image: url(" + url + ")");
	}

	public static StyleEvent backgroundImage(final String mimeType, final byte[] imageData) {
		return new StyleEvent(String.format("background-image: url(\"data:%s,%s\")",
				Nullables.orElse(mimeType, "image"),
				encodeByteArrayToUrlString(imageData)));
	}

	private final String style;

	public StyleEvent(final String style) {
		this.style = style;
	}

	public String getStyle() {
		return style;
	}
}
