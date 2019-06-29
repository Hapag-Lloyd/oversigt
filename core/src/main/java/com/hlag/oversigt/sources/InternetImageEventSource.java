package com.hlag.oversigt.sources;

import java.io.IOException;

import com.hlag.oversigt.core.eventsource.EventSource;
import com.hlag.oversigt.sources.event.StyleEvent;
import com.hlag.oversigt.util.Tuple;

@EventSource(view = "ImageView",
		displayName = "Internet Image",
		description = "Load an image from the internet by parsing a web site and show the image in the dashboard.",
		dataItems = { "width" },
		hiddenDataItems = { "more-info" })
public class InternetImageEventSource extends AbstractDownloadEventSource<StyleEvent> {
	public InternetImageEventSource() {
		// no fields to be initialized
	}

	@Override
	protected StyleEvent produceEvent() {
		try {
			final Tuple<byte[], String> image = downloadBytes(createConfiguredConnection());
			return StyleEvent.backgroundImage(image.getSecond(), image.getFirst());
		} catch (final IOException e) {
			return failure("Unable to download image: " + e.getMessage(), e);
		}
	}
}
