package com.hlag.oversigt.sources;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hlag.oversigt.core.event.OversigtEvent;
import com.hlag.oversigt.core.eventsource.EventSourceTestHelper;
import com.hlag.oversigt.sources.AbstractDownloadEventSource.InternetAddress;
import com.hlag.oversigt.sources.InternetTextExtractionEventSource.ValueExtraction;
import com.hlag.oversigt.sources.event.TwoColumnListEvent;
import com.hlag.oversigt.util.JsonUtilsTest;

public class InternetTextExtractionEventSourceTest extends AbstractServerBasedTest {
	// @Test
	// public void downloadText_success_withLogin() {
	// // set up server
	// final String textBody = "just some text";
	// givenThat(post("/content")//
	// .withRequestBody(new EqualToPattern("l_username=max&l_password=12345"))
	// .willReturn(aResponse()//
	// .withHeader("Content-Type", "text/plain")
	// .withBody(textBody)
	// .withStatus(200)));
	//
	// // set up event source
	// final InternetTextExtractionEventSource eventSource
	// =
	// EventSourceTestHelper.createScheduledEventSource(InternetTextExtractionEventSource.class);
	// eventSource.setCheckSSL(false);
	// eventSource.setCredentials(new Credentials(1, "abc", "def", "12345", "ww"));
	// eventSource.setUrls(new InternetAddress[] {
	// new InternetAddress(String.format("http://%s:%s/content", "localhost",
	// getServerMock().port()),
	// "",
	// new LoginData[] {
	// new LoginData("l_username", "max"),
	// new LoginData("l_password", "${password}") }) });
	// eventSource.setValueExtractions(new ValueExtraction[] {});
	//
	// // expected
	// final String expectedText = "just some text";
	//
	// // run event source
	// final Optional<OversigtEvent> event =
	// EventSourceTestHelper.runEventSource(eventSource);
	//
	// // checks
	// assertThat(event).isPresent();
	// assertThat(event).get().isInstanceOf(TwoColumnListEvent.class);
	// final TwoColumnListEvent<?> realEvent = (TwoColumnListEvent<?>) event.get();
	// assertThat(realEvent.getItems().get(0).getLabel()).isEqualTo(expectedText);
	// }
	@Test
	public void downloadText_success_withRedirect() {
		// set up server
		final String jsonBody = "[{\"name\":\"vega\"}]";
		givenThat(get("/first")//
				.willReturn(aResponse()//
						.withHeader("Location", "/second")
						.withStatus(303)));
		givenThat(get("/second")//
				.willReturn(aResponse()//
						.withHeader("Content-Type", "application/json")
						.withBody(jsonBody)
						.withStatus(200)));

		// set up event source
		final InternetTextExtractionEventSource eventSource
				= EventSourceTestHelper.createScheduledEventSource(InternetTextExtractionEventSource.class,
						JsonUtilsTest.createModulesForJsonTesting());
		eventSource.setCheckSSL(false);
		eventSource.setUrls(new InternetAddress[] {
				new InternetAddress(String.format("http://%s:%s/first", "localhost", getServerMock().port()), "") });
		eventSource.setValueExtractions(new ValueExtraction[] { new ValueExtraction("true", "${jsonpath:$[*].name}") });

		// expected
		final String expectedName = "vega";

		// run event source
		final Optional<OversigtEvent> event = EventSourceTestHelper.runEventSource(eventSource);

		// checks
		assertThat(event).isPresent();
		assertThat(event).get().isInstanceOf(TwoColumnListEvent.class);
		final TwoColumnListEvent<?> realEvent = (TwoColumnListEvent<?>) event.get();
		assertThat(realEvent.getItems().get(0).getLabel()).isEqualTo(expectedName);
	}
}
