package com.hlag.oversigt.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.io.Resources;

@ExtendWith(MockitoExtension.class)
public class JdbcDatabaseTest {
	private static final String DATABASE_FILENAME = "JdbcDatabaseTest.db";

	private static Optional<Path> tempFolder = Optional.empty();

	@BeforeAll
	public static void initTempFolder() throws IOException {
		tempFolder = Optional.of(Files.createTempDirectory("oversigt"));
	}

	@AfterAll
	public static void deleteTempFolder() {
		tempFolder.map(Path::toFile).ifPresent(dir -> {
			try {
				FileUtils.deleteDirectory(dir);
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private Optional<Path> dbLocation = Optional.empty();

	private Optional<JdbcDatabase> database = Optional.empty();

	@SuppressWarnings("resource")
	@BeforeEach
	public void prepareDatabase() throws IOException, URISyntaxException {
		final URL db = Resources.getResource(DATABASE_FILENAME);
		dbLocation = tempFolder.map(dir -> dir.resolve(DATABASE_FILENAME));
		Files.copy(Paths.get(db.toURI()), dbLocation.get());
		database = Optional
				.of(new JdbcDatabase(new SqliteDialect(), dbLocation.get().toAbsolutePath().toString(), "", "", ""));
	}

	@AfterEach
	public void shutdownDatabase() throws IOException {
		database.get().close();
		Files.delete(dbLocation.get());
	}

	@Test
	public void shouldReadDashboardIds() {
		final List<String> expected = Arrays.asList("Test");
		final List<String> actual = database.get().getDashboardIds();

		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	public void shouldReadEventSourceNames() {
		final List<String> expected = Arrays.asList("Internet Image",
				"Restaurant HafenCity",
				"Jira Bar",
				"GWRP Mailbox",
				"Exchange Tasks",
				"Timeline",
				"Exchange Room Availability",
				"Motivation",
				"P08 Gen");
		final List<String> actual = database.get().getEventSourceNames();

		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}
}
