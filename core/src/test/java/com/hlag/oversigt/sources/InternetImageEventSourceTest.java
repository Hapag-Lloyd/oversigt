package com.hlag.oversigt.sources;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.jenspiegsa.wiremockextension.WireMockExtension;
import com.github.jenspiegsa.wiremockextension.WireMockSettings;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSourceTestHelper;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.properties.HttpProxy;
import com.hlag.oversigt.sources.AbstractDownloadEventSource.HttpHeader;
import com.hlag.oversigt.sources.AbstractDownloadEventSource.InternetAddress;
import com.hlag.oversigt.sources.event.StyleEvent;

@ExtendWith(WireMockExtension.class)
@WireMockSettings(failOnUnmatchedRequests = true)
public class InternetImageEventSourceTest extends AbstractServerBasedTest {
	@Test
	public void downloadImage_success() throws IOException {
		// set up server
		final String htmlBody = readResourceAsString("eventSources/dilbert.com.html");
		final byte[] imageBody = readResourceAsByteArray("eventSources/dilbert.com.gif");
		givenThat(get("/")//
				.withBasicAuth("test", "1234")
				.withHeader("x-header", new EqualToPattern("required"))
				.willReturn(aResponse()//
						.withBody(htmlBody)
						.withHeader("Set-Cookie", "SomeCookie=abcd")
						.withHeader("Content-Type", "text/html")
						.withStatus(200)));
		givenThat(get("/url-to-image/b358b87014570138dd90005056a9545d")
				.withCookie("SomeCookie", new EqualToPattern("abcd"))
				.willReturn(aResponse()//
						.withBody(imageBody)
						.withHeader("Content-Type", "image/gif")
						.withStatus(200)));

		// set up event source
		final InternetImageEventSource eventSource
				= EventSourceTestHelper.createScheduledEventSource(InternetImageEventSource.class);
		eventSource.setCheckSSL(false);
		eventSource.setCredentials(new Credentials(1, "test", "test", "1234", ""));
		eventSource.setUrls(new InternetAddress[] {
				new InternetAddress(String.format("http://%s:%s/", "localhost", getServerMock().port()),
						"<img[^>]*src=\"(?:https?:)?(/url-to-image/[a-zA-Z0-9]+)\"[^>]*/?>"),
				new InternetAddress(String.format("http://%s:%s${1.1}", "localhost", getServerMock().port()), "") });
		eventSource.setHttpHeaders(new HttpHeader[] { new HttpHeader("x-header", "required") });
		eventSource.setHttpProxy(HttpProxy.EMPTY);

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

	@Test
	public void downloadImage_fail() {
		// set up server
		givenThat(get("/")//
				.willReturn(aResponse()//
						.withStatus(404)));

		// set up event source
		final InternetImageEventSource eventSource
				= EventSourceTestHelper.createScheduledEventSource(InternetImageEventSource.class);
		eventSource.setCheckSSL(false);
		eventSource.setUrls(new InternetAddress[] {
				new InternetAddress(String.format("http://%s:%s/", "localhost", getServerMock().port()),
						"<img[^>]*src=\"(?:https?:)?(/url-to-image/[a-zA-Z0-9]+)\"[^>]*/?>"),
				new InternetAddress(String.format("http://%s:%s${1.1}", "localhost", getServerMock().port()), "") });

		// expected
		final Class<? extends OversigtEvent> expectedEventClass = EventSourceTestHelper.getErrorEventClass();

		// run event source
		final Optional<OversigtEvent> event = EventSourceTestHelper.runEventSource(eventSource);

		// checks
		assertThat(event).isPresent();
		assertThat(event).get().isInstanceOf(expectedEventClass);
	}
}
