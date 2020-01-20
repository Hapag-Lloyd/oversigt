package com.hlag.oversigt.sources;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.jenspiegsa.wiremockextension.ConfigureWireMock;
import com.github.jenspiegsa.wiremockextension.InjectServer;
import com.github.jenspiegsa.wiremockextension.WireMockExtension;
import com.github.jenspiegsa.wiremockextension.WireMockSettings;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.Options;
import com.google.common.io.Resources;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSourceTestHelper;
import com.hlag.oversigt.sources.AbstractDownloadEventSource.InternetAddress;
import com.hlag.oversigt.sources.event.StyleEvent;

import edu.umd.cs.findbugs.annotations.Nullable;

@ExtendWith(WireMockExtension.class)
@WireMockSettings(failOnUnmatchedRequests = true)
public class InternetImageEventSourceTest {
	@InjectServer
	@Nullable
	private WireMockServer serverMock;

	@ConfigureWireMock
	private Options options = wireMockConfig().dynamicPort().notifier(new ConsoleNotifier(false));

	@Test
	public void downloadImage() throws IOException {
		// set up server
		final String htmlBody
				= Resources.toString(Resources.getResource("eventSources/dilbert.com.html"), StandardCharsets.UTF_8);
		final byte[] imageBody = Resources.toByteArray(Resources.getResource("eventSources/dilbert.com.gif"));
		givenThat(get("/")
				.willReturn(aResponse().withBody(htmlBody).withHeader("Content-Type", "text/html").withStatus(200)));
		givenThat(get("/url-to-image/b358b87014570138dd90005056a9545d")
				.willReturn(aResponse().withBody(imageBody).withHeader("Content-Type", "image/gif").withStatus(200)));

		// set up event source
		final InternetImageEventSource eventSource
				= EventSourceTestHelper.createScheduledEventSource(InternetImageEventSource.class);
		eventSource.setCheckSSL(false);
		eventSource.setUrls(new InternetAddress[] {
				new InternetAddress(
						String.format("http://%s:%s/", "localhost", Objects.requireNonNull(serverMock).port()),
						"<img[^>]*src=\"(?:https?:)?(/url-to-image/[a-zA-Z0-9]+)\"[^>]*/?>"),
				new InternetAddress(
						String.format("http://%s:%s${1.1}", "localhost", Objects.requireNonNull(serverMock).port()),
						"") });

		// expected
		final Class<StyleEvent> expectedEventClass = StyleEvent.class;
		final String expectedStyleStart = "background-image: url(\"data:image/gif,GIF89a%84%03%18%01%F7";

		// run event source
		final Optional<OversigtEvent> event = EventSourceTestHelper.runEventSource(eventSource);

		// checks
		assertThat(event).isPresent();
		assertThat(event).get().isInstanceOf(expectedEventClass);
		final StyleEvent styleEvent = (StyleEvent) event.get();
		assertThat(styleEvent).extracting(StyleEvent::getStyle).asString().startsWith(expectedStyleStart);
	}
}
