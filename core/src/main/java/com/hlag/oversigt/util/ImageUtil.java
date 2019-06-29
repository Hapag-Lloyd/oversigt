package com.hlag.oversigt.util;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.CaseFormat;
import com.hlag.oversigt.core.Oversigt;
import com.hlag.oversigt.model.EventSourceDescriptor;

public final class ImageUtil {
	private ImageUtil() {
		throw new RuntimeException();
	}

	private static final Map<EventSourceDescriptor, String> IMAGE_URLS = new HashMap<>();

	private static boolean isFileAvailable(final String path) {
		try (InputStream in = Oversigt.class.getClassLoader().getResourceAsStream(path)) {
			return in != null;
		} catch (final Exception ignore) {
			return false;
		}
	}

	public static String getPreviewImageUrl(final EventSourceDescriptor info) {
		return IMAGE_URLS.computeIfAbsent(info,
				i -> getPreviewImageUrl(Optional.ofNullable(info.getServiceClass()).map(Class::getName).orElse(null),
						i.getView()));
	}

	private static String getPreviewImageUrl(final String serviceClassName, final String viewName) {
		if (serviceClassName != null) {
			if (isFileAvailable(getResourcePathForPreview(serviceClassName))) {
				return getUrlPathForPreview(serviceClassName);
			}
		}
		if (isFileAvailable(getResourcePathForWidget(viewName))) {
			return getUrlPathForWidget(viewName);
		}
		return "http://placehold.it/500x300";
	}

	private static String getResourcePathForPreview(final String serviceClassName) {
		return "statics/preview/" + serviceClassName + ".png";
	}

	private static String getUrlPathForPreview(final String serviceClassName) {
		return "/assets/preview/" + serviceClassName + ".png";
	}

	private static String getResourcePathForWidget(final String widget) {
		final String widgetResourceName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, widget);
		return "statics/widgets/" + widgetResourceName + "/" + widgetResourceName + ".png";
	}

	private static String getUrlPathForWidget(final String widget) {
		return "/assets/widgets/" + widget + "/" + widget + ".png";
	}
}
