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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.io.Resources;
import com.hlag.oversigt.util.JsonUtils;

@RunWith(MockitoJUnitRunner.class)
public class JdbcDatabaseTest {
	private static final String DATABASE_FILENAME = "JdbcDatabaseTest.db";

	private static Optional<Path> tempFolder = Optional.empty();

	@BeforeClass
	public static void initTempFolder() throws IOException {
		tempFolder = Optional.of(Files.createTempDirectory("oversigt"));
	}

	@AfterClass
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
	@Before
	public void prepareDatabase() throws IOException, URISyntaxException {
		final URL db = Resources.getResource(DATABASE_FILENAME);
		dbLocation = tempFolder.map(dir -> dir.resolve(DATABASE_FILENAME));
		Files.copy(Paths.get(db.toURI()), dbLocation.get());
		database = Optional.of(new JdbcDatabase(new SqliteDialect(),
				dbLocation.get().toAbsolutePath().toString(),
				"",
				"",
				"",
				new JsonUtils()/* TODO create an injected instance */));
	}

	@After
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
	public void shouldReadDashboardIds2() {
		final List<String> expected = Arrays.asList("Test");
		final List<String> actual = database.get().getDashboardIds();

		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}
}
