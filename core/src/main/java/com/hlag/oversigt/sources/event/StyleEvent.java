package com.hlag.oversigt.sources.event;

import static com.hlag.oversigt.util.HttpUtils.encodeByteArrayToUrlString;

import com.hlag.oversigt.core.OversigtEvent;

public class StyleEvent extends OversigtEvent {
	public static StyleEvent backgroundImage(String url) {
		return new StyleEvent("background-image: url(" + url + ")");
	}

	public static StyleEvent backgroundImage(String mimeType, byte[] imageData) {
		mimeType = mimeType != null ? mimeType : "image";
		return new StyleEvent(
				"background-image: url(\"data:" + mimeType + "," + encodeByteArrayToUrlString(imageData) + "\")");
	}

	private final String style;

	public StyleEvent(String style) {
		this.style = style;
	}

	public String getStyle() {
		return style;
	}
}