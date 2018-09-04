package com.hlag.oversigt.sources;

import java.io.IOException;

import com.hlag.oversigt.core.EventSource;
import com.hlag.oversigt.sources.event.StyleEvent;
import com.hlag.oversigt.util.Tuple;

@EventSource(view = "ImageView", displayName = "Internet Image", description = "Load an image from the internet by parsing a web site and show the image in the dashboard.", dataItems = {
		"width" })
public class InternetImageEventSource extends AbstractDownloadEventSource<StyleEvent> {
	@Override
	protected StyleEvent produceEvent() {
		try {
			Tuple<byte[], String> image = downloadBytes(createConfiguredConnection());
			return StyleEvent.backgroundImage(image.getSecond(), image.getFirst());
		} catch (IOException e) {
			return failure("Unable to download image: " + e.getMessage(), e);
		}
	}
}
