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
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.io.Resources;
import com.hlag.oversigt.util.Utils.OperatingSystemType;

import de.larssh.utils.SneakyException;
import de.larssh.utils.function.ThrowingConsumer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public final class FileUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

	public static Stream<Path> closedPathStream(final Stream<Path> stream) {
		try (Stream<Path> paths = stream) {
			return paths.collect(Collectors.toList()).stream();
		}
	}

	public static void deleteFolderOnExit(final Path root) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteFolder(root), "DeleteOnExit:" + root.toString()));
	}

	public static void deleteFolder(final Path root) {
		try {
			LOGGER.info("Deleting folder [{}]", root.toAbsolutePath().toString());
			Files.walk(root)
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

	public static Optional<URL> getResourceUrl(final String path) {
		try {
			return Optional.of(Resources.getResource(path));
		} catch (@SuppressWarnings("unused") final IllegalArgumentException ignore) {
			return Optional.empty();
		}
	}

	public static FileSystem getFileSystem(final URI uri) {
		try {
			return FileSystems.getFileSystem(uri);
		} catch (@SuppressWarnings("unused") final FileSystemNotFoundException e) {
			try {
				return FileSystems.newFileSystem(uri, Collections.emptyMap());
			} catch (final IOException ioException) {
				throw new UncheckedIOException(ioException);
			}
		}
	}

	public static Stream<Path> streamResourcesFromClasspath() {
		return getClasspathEntries().stream().map(Paths::get).flatMap(FileUtils::streamResources);
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
					.map(classpath -> Splitter.on(CharMatcher.whitespace()).omitEmptyStrings().splitToList(classpath))
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

	private static final String REGEX_META_CHARS = ".^$+{[]|()";

	private static final String GLOB_META_CHARS = "\\*?[{";

	private static final char EOL = 0; // TBD

	private static boolean isRegexMeta(final char c) {
		return REGEX_META_CHARS.indexOf(c) != -1;
	}

	private static boolean isGlobMeta(final char c) {
		return GLOB_META_CHARS.indexOf(c) != -1;
	}

	private static char next(final String glob, final int i) {
		if (i < glob.length()) {
			return glob.charAt(i);
		}
		return EOL;
	}

	/**
	 * Creates a regex pattern from the given glob expression.
	 *
	 * @param globPattern the glob to turn into a regex
	 * @param isDos       whether to use DOS/ windows specific syntax or not
	 * @return the converted regex
	 * @throws PatternSyntaxException if the given syntax has errors
	 * @see <a href=
	 *      "https://github.com/rtyley/globs-for-java/blob/master/src/main/java/com/madgag/globs/openjdk/Globs.java">OpenJDK</a>
	 */
	@SuppressWarnings({ "checkstyle:DescendantToken", "checkstyle:InnerAssignment" })
	@SuppressFBWarnings(value = "CC_CYCLOMATIC_COMPLEXITY", justification = "copied from OpenJDK as is")
	private static String toRegexPattern(final String globPattern, final boolean isDos) {
		boolean inGroup = false;
		// final StringBuilder regex = new StringBuilder("^");
		final StringBuilder regex = new StringBuilder();

		int i = 0;
		while (i < globPattern.length()) {
			char c = globPattern.charAt(i++);
			switch (c) {
			case '\\':
				// escape special characters
				if (i == globPattern.length()) {
					throw new PatternSyntaxException("No character to escape", globPattern, i - 1);
				}
				final char next = globPattern.charAt(i++);
				if (isGlobMeta(next) || isRegexMeta(next)) {
					regex.append('\\');
				}
				regex.append(next);
				break;
			case '/':
				if (isDos) {
					regex.append("\\\\");
				} else {
					regex.append(c);
				}
				break;
			case '[':
				// don't match name separator in class
				if (isDos) {
					regex.append("[[^\\\\]&&[");
				} else {
					regex.append("[[^/]&&[");
				}
				if (next(globPattern, i) == '^') {
					// escape the regex negation char if it appears
					regex.append("\\^");
					i += 1;
				} else {
					// negation
					if (next(globPattern, i) == '!') {
						regex.append('^');
						i += 1;
					}
					// hyphen allowed at start
					if (next(globPattern, i) == '-') {
						regex.append('-');
						i += 1;
					}
				}
				boolean hasRangeStart = false;
				char last = 0;
				while (i < globPattern.length()) {
					c = globPattern.charAt(i++);
					if (c == ']') {
						break;
					}
					if (c == '/' || isDos && c == '\\') {
						throw new PatternSyntaxException("Explicit 'name separator' in class", globPattern, i - 1);
					}
					// TBD: how to specify ']' in a class?
					if (c == '\\' || c == '[' || c == '&' && next(globPattern, i) == '&') {
						// escape '\', '[' or "&&" for regex class
						regex.append('\\');
					}
					regex.append(c);

					if (c == '-') {
						if (!hasRangeStart) {
							throw new PatternSyntaxException("Invalid range", globPattern, i - 1);
						}
						if ((c = next(globPattern, i++)) == EOL || c == ']') {
							break;
						}
						if (c < last) {
							throw new PatternSyntaxException("Invalid range", globPattern, i - 3);
						}
						regex.append(c);
						hasRangeStart = false;
					} else {
						hasRangeStart = true;
						last = c;
					}
				}
				if (c != ']') {
					throw new PatternSyntaxException("Missing ']", globPattern, i - 1);
				}
				regex.append("]]");
				break;
			case '{':
				if (inGroup) {
					throw new PatternSyntaxException("Cannot nest groups", globPattern, i - 1);
				}
				regex.append("(?:(?:");
				inGroup = true;
				break;
			case '}':
				if (inGroup) {
					regex.append("))");
					inGroup = false;
				} else {
					regex.append('}');
				}
				break;
			case ',':
				if (inGroup) {
					regex.append(")|(?:");
				} else {
					regex.append(',');
				}
				break;
			case '*':
				if (next(globPattern, i) == '*') {
					// crosses directory boundaries
					regex.append(".*");
					i += 1;
				} else {
					// within directory boundary
					if (isDos) {
						regex.append("[^\\\\]*");
					} else {
						regex.append("[^/]*");
					}
				}
				break;
			case '?':
				if (isDos) {
					regex.append("[^\\\\]");
				} else {
					regex.append("[^/]");
				}
				break;

			default:
				if (isRegexMeta(c)) {
					regex.append('\\');
				}
				regex.append(c);
				break;
			}
		}

		if (inGroup) {
			throw new PatternSyntaxException("Missing '}", globPattern, i - 1);
		}

		return regex.append('$').toString();
	}

	public static String toUnixRegexPattern(final String globPattern) {
		return toRegexPattern(globPattern, false);
	}

	public static String toWindowsRegexPattern(final String globPattern) {
		return toRegexPattern(globPattern, true);
	}

	public static String toRegex(final String globPattern) {
		if (Utils.getOperatingSystemType() == OperatingSystemType.Windows) {
			return toWindowsRegexPattern(globPattern);
		}
		return toUnixRegexPattern(globPattern);
	}

	private FileUtils() {
		throw new UnsupportedOperationException();
	}
}
