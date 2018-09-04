package com.hlag.oversigt.launcher;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OversigtLauncher {
	private static final String DELIMITER = "[;:]";

	// We cannot use Oversigt class as reference here as the class loader is not ready, yet!
	private static final String MAIN_CLASS = "com.hlag.oversigt.core.Oversigt";

	private static final Path HERE = Paths.get(".").toAbsolutePath();

	private static final Path BIN = HERE.resolve("bin");

	private static final Path LIB = HERE.resolve("lib");
	private static final Path CP = LIB.resolve("classpath");

	private static final Path LIB_USER = HERE.resolve("lib-user");

	private static final Path RES = HERE.resolve("res");

	private static final Path DATA = HERE.resolve("data");
	private static final Path DB = DATA.resolve("dashboard.db");

	public static void main(String[] args) throws Exception {
		// create folders
		Files.createDirectories(DATA);

		// create backups
		createBackup(DB);
		createBackup(DATA.resolve("config.json"));

		// collect jars
		Stream<URL> resourcePaths = Arrays//
				.stream(new Path[] { DATA, RES })//
				.map(OversigtLauncher::toURL);

		Stream<URL> mainJars = closedPath(Files.list(BIN))//
				.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
				.map(Path::toAbsolutePath)
				.map(OversigtLauncher::toURL);

		Stream<URL> classpathJars = Files//
				.lines(CP)
				.map(s -> s.replace('\\', '/'))
				.flatMap(s -> Arrays.stream(s.split(DELIMITER)))
				.map(s -> "lib/" + s.substring(s.lastIndexOf("/") + 1))
				.map(Paths::get)
				.map(OversigtLauncher::toURL);

		// find user jars
		Stream<URL> userJars = closedPath(Files.walk(LIB_USER))//
				.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))//
				.map(Path::toAbsolutePath)//
				.map(OversigtLauncher::toURL);

		URL[] urls = concat(resourcePaths, mainJars, classpathJars, userJars)//
				.collect(Collectors.toList())
				.toArray(new URL[0]);

		// check if urls
		Stream.of(urls).forEach(
				url -> System.out.println(url + " -> " + (new File(url.getFile()).exists() ? "OK" : "missing")));

		// prepare arguments
		String[] arguments = new String[args.length + 1];
		arguments[0] = "--startEventSources";
		System.arraycopy(args, 0, arguments, 1, args.length);

		// start application
		URLClassLoader classLoader = URLClassLoader.newInstance(urls);
		Thread.currentThread().setContextClassLoader(classLoader);
		Class<?> mainClass = classLoader.loadClass(MAIN_CLASS);
		Method mainMethod = mainClass.getMethod("main", String[].class);
		mainMethod.invoke(null, new Object[] { arguments });
	}

	private static Path getBackupPath(Path path) {
		return path.getParent()
				.resolve(path.getFileName().toString() + "." + LocalDateTime.now().toString().replace(':', '.'));
	}

	private static void createBackup(Path path) throws IOException {
		if (Files.exists(path)) {
			Path backupPath = getBackupPath(path);
			Files.copy(path, backupPath);
			try (Stream<Path> files = Files.list(backupPath.getParent())) {
				files.filter(p -> p.toString().startsWith(path.toString()) && !p.equals(path))
						.sorted((p1, p2) -> lastModified(p2).compareTo(lastModified(p1)))
						.skip(5)
						.forEach(OversigtLauncher::delete);
			}
		}
	}

	@SafeVarargs
	private static <T> Stream<T> concat(Stream<T>... streams) {
		Stream<T> out = streams[0];
		for (int i = 1; i < streams.length; ++i) {
			out = Stream.concat(out, streams[i]);
		}
		return out;
	}

	private static Stream<Path> closedPath(Stream<Path> stream) {
		try (Stream<Path> paths = stream) {
			return paths.collect(Collectors.toList()).stream();
		}
	}

	private static URL toURL(Path path) {
		try {
			return path.toUri().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	private static FileTime lastModified(Path path) {
		try {
			return Files.getLastModifiedTime(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void delete(Path path) {
		try {
			Files.delete(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
