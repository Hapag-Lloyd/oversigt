package com.hlag.oversigt.sources;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.extension.ExtendWith;

import com.github.jenspiegsa.wiremockextension.ConfigureWireMock;
import com.github.jenspiegsa.wiremockextension.InjectServer;
import com.github.jenspiegsa.wiremockextension.WireMockExtension;
import com.github.jenspiegsa.wiremockextension.WireMockSettings;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.Options;
import com.google.common.io.Resources;

import edu.umd.cs.findbugs.annotations.Nullable;

@ExtendWith(WireMockExtension.class)
@WireMockSettings(failOnUnmatchedRequests = true)
public class AbstractServerBasedTest {
	@InjectServer
	@Nullable
	private WireMockServer serverMock;

	@ConfigureWireMock
	private Options options = wireMockConfig().dynamicPort().notifier(new ConsoleNotifier(false));

	protected WireMockServer getServerMock() {
		return Objects.requireNonNull(serverMock);
	}

	protected static String readResourceAsString(final String path) throws IOException {
		return Resources.toString(Resources.getResource(path), StandardCharsets.UTF_8);
	}

	protected static byte[] readResourceAsByteArray(final String path) throws IOException {
		return Resources.toByteArray(Resources.getResource(path));
	}
}
