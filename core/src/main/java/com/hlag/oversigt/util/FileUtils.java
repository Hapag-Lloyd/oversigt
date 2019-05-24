package com.hlag.oversigt.util;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import de.larssh.utils.SneakyException;
import de.larssh.utils.function.ThrowingConsumer;

public final class FileUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

	public static Stream<Path> closedPathStream(final Stream<Path> stream) {
		try (Stream<Path> paths = stream) {
			return paths//
					.collect(Collectors.toList())
					.stream();
		}
	}

	public static void deleteFolderOnExit(final Path root) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteFolder(root), "DeleteOnExit:" + root.toString()));
	}

	public static void deleteFolder(final Path root) {
		try {
			LOGGER.info("Deleting folder [{}]", root.toAbsolutePath().toString());
			Files.walk(root)//
					.sorted(Comparator.reverseOrder())
					.forEach(ThrowingConsumer.throwing(Files::deleteIfExists));
		} catch (final IOException e) {
			throw new SneakyException(e);
		}
	}

	public static URI getURI(final URL url) {
		try {
			return url.toURI();
		} catch (final URISyntaxException e) {
			throw new SneakyException(e);
		}
	}

	public static Path getPath(final URI uri) {
		if ("jar".equalsIgnoreCase(uri.getScheme())) {
			final String uriString = uri.toString();
			final List<String> jarPathParts = Splitter.on('!').limit(2).splitToList(uriString);
			if (jarPathParts.size() == 2) {
				return getFileSystem(URI.create(jarPathParts.get(0))).getPath(jarPathParts.get(1));
			}
			throw new RuntimeException("Unable to interpret path: " + uri);
		}
		return Paths.get(uri);
	}

	public static FileSystem getFileSystem(final URI uri) {
		try {
			return FileSystems.getFileSystem(uri);
		} catch (final FileSystemNotFoundException e) {
			try {
				return FileSystems.newFileSystem(uri, Collections.emptyMap());
			} catch (final IOException ioException) {
				throw new UncheckedIOException(ioException);
			}
		}
	}

	public static Stream<Path> streamResourcesFromClasspath() {
		return getClasspathEntries()//
				.stream()
				.map(Paths::get)
				.flatMap(FileUtils::streamResources);
	}

	private static Stream<Path> streamResources(final Path classpathEntry) {
		if (!Files.isRegularFile(classpathEntry)) {
			return streamResourcesFromDirectory(classpathEntry);
		}
		final Optional<String> extension = getExtension(classpathEntry);
		if (extension.isPresent()) {
			switch (extension.get().toLowerCase()) {
			case "jar":
			case "zip":
				return listResourcesFromJar(classpathEntry).stream();
			default:
				// nothing
			}
		}
		throw new RuntimeException("Unable to handle file for resource scanning: " + classpathEntry);
	}

	@SuppressWarnings("resource")
	private static List<Path> listResourcesFromJar(final Path zip) {
		final String uriString = "jar:" + zip.toUri().toString();

		final FileSystem fileSystem = getFileSystem(URI.create(uriString));
		final LinkedList<Path> paths = new LinkedList<>();
		try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(zip))) {
			JarEntry entry = null;
			while ((entry = jarInputStream.getNextJarEntry()) != null) {
				paths.add(fileSystem.getPath(entry.getName()));
			}

			// take the JAR entry...
			Optional.of(jarInputStream)
					// ... and extract the manifest entry...
					.map(JarInputStream::getManifest)
					// ... read its main attributes ...
					.map(Manifest::getMainAttributes)
					// ... take the ClassPath entry ...
					.map(attributes -> attributes.getValue("Class-Path"))
					// ... split it on whitespaces. Then you have a list of new relative class path
					// entries.
					.map(classpath -> Splitter//
							.on(CharMatcher.whitespace())
							.omitEmptyStrings()
							.splitToList(classpath))
					// take them, resolve the corresponding files and recursivly call this method
					.map(jarClasspathEntries -> jarClasspathEntries.stream()
							.map(zip.toAbsolutePath().getParent()::resolve)
							.filter(Files::exists)
							.map(FileUtils::listResourcesFromJar)
							.flatMap(Collection::stream)
							.collect(toList()))
					.ifPresent(paths::addAll);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}

		return paths;
	}

	private static Stream<Path> streamResourcesFromDirectory(final Path directory) {
		try {
			return closedPathStream(Files.walk(directory));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<String> getClasspathEntries() {
		return Splitter.on(File.pathSeparatorChar)
				.omitEmptyStrings()
				.trimResults()
				.splitToList(System.getProperty("java.class.path"));
	}

	public static Optional<String> getExtension(final Path path) {
		if (Files.isRegularFile(path)) {
			return getExtension(path.getFileName().toString());
		}
		return Optional.empty();
	}

	public static Optional<String> getExtension(final String filename) {
		final int lastIndex = filename.lastIndexOf('.');
		if (lastIndex < 0) {
			return Optional.empty();
		}
		return Optional.of(filename.substring(lastIndex + 1));
	}

	private FileUtils() {
		throw new UnsupportedOperationException();
	}
}
