package com.hlag.oversigt.util;

import static com.hlag.oversigt.util.SneakyException.sneakc;

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

public class FileUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

	public static Stream<Path> closedPath(Stream<Path> stream) {
		try (Stream<Path> paths = stream) {
			return paths//
				.collect(Collectors.toList())
				.stream();
		}
	}

	public static void deleteFolderOnExit(Path root) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteFolder(root), "DeleteOnExit:" + root.toString()));
	}

	public static void deleteFolder(Path root) {
		try {
			LOGGER.info("Deleting folder [{}]", root.toAbsolutePath().toString());
			Files
				.walk(root)//
				.sorted(Comparator.reverseOrder())
				.forEach(sneakc(Files::deleteIfExists));
		} catch (IOException e) {
			throw new SneakyException(e);
		}
	}

	public static URI getURI(URL url) {
		try {
			return url.toURI();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public static Path getPath(URI uri) {
		if ("jar".equalsIgnoreCase(uri.getScheme())) {
			String uriString = uri.toString();
			List<String> jarPathParts = Splitter.on('!').limit(2).splitToList(uriString);
			if (jarPathParts.size() == 2) {
				return getFileSystem(URI.create(jarPathParts.get(0))).getPath(jarPathParts.get(1));
			} else {
				throw new RuntimeException("Unable to interpret path: " + uri);
			}
		} else {
			return Paths.get(uri);
		}
	}

	public static FileSystem getFileSystem(URI uri) {
		try {
			return FileSystems.getFileSystem(uri);
		} catch (FileSystemNotFoundException e) {
			try {
				return FileSystems.newFileSystem(uri, Collections.emptyMap());
			} catch (IOException e1) {
				throw new UncheckedIOException(e1);
			}
		}
	}

	public static Stream<Path> listResourcesFromClasspath() {
		return getClasspathEntries()//
			.stream()
			.map(Paths::get)
			.flatMap(FileUtils::listResources);
	}

	private static Stream<Path> listResources(Path classpathEntry) {
		if (classpathEntry.toString().toLowerCase().endsWith(".jar") //
				|| classpathEntry.toString().toLowerCase().endsWith(".zip")) {
			return listResourcesFromJar(classpathEntry).stream();
		} else {
			return listResourcesFromDirectory(classpathEntry);
		}
	}

	private static List<Path> listResourcesFromJar(Path zip) {
		final String uriString = "jar:" + zip.toUri().toString();

		FileSystem fileSystem = getFileSystem(URI.create(uriString));
		LinkedList<Path> paths = new LinkedList<>();
		try (JarInputStream jis = new JarInputStream(Files.newInputStream(zip))) {
			JarEntry entry = null;
			while ((entry = jis.getNextJarEntry()) != null) {
				paths.add(fileSystem.getPath(entry.getName()));
			}

			Optional<String> classpath = Optional
				.of(jis)
				.map(JarInputStream::getManifest)
				.map(Manifest::getMainAttributes)
				.map(ma -> ma.getValue("Class-Path"));
			if (classpath.isPresent()) {
				List<String> entries = Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().splitToList(classpath.get());
				entries//
					.stream()
					.map(zip.getParent()::resolve)
					.filter(Files::exists)
					.forEach(FileUtils::listResourcesFromJar);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return paths;
	}

	private static Stream<Path> listResourcesFromDirectory(Path directory) {
		try {
			return closedPath(Files.walk(directory));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<String> getClasspathEntries() {
		return Splitter//
			.on(File.pathSeparatorChar)
			.omitEmptyStrings()
			.trimResults()
			.splitToList(System.getProperty("java.class.path"));
	}
}
