package com.hlag.oversigt.sources;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialItem.InformationalMessage;
import org.eclipse.jgit.transport.CredentialItem.Password;
import org.eclipse.jgit.transport.CredentialItem.Username;
import org.eclipse.jgit.transport.CredentialItem.YesNoType;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

import com.hlag.oversigt.core.OversigtEvent;
import com.hlag.oversigt.core.eventsource.annotation.Property;
import com.hlag.oversigt.properties.Credentials;
import com.hlag.oversigt.util.FileUtils;
import com.hlag.oversigt.util.LazyInitializedReference;

public abstract class AbstractGitEventSource<E extends OversigtEvent> extends AbstractSslAwareEventSource<E> {
	private String repositoryUrl;
	private Credentials credentials = Credentials.EMPTY;
	private TemporalAmount lookBack = Period.ofWeeks(1);

	private LazyInitializedReference<Git> git = new LazyInitializedReference<>(this::createGitRepository);

	@Property(name = "Look Back", description = "The amount of time to look into the past. Leave emtpy to use all commits.")
	public TemporalAmount getLookBack() {
		return lookBack;
	}

	public void setLookBack(TemporalAmount lookBack) {
		this.lookBack = lookBack;
	}

	@Property(name = "Repository URL", description = "The URL to the repository to inspect.")
	public String getRepositoryUrl() {
		return repositoryUrl;
	}

	public void setRepositoryUrl(String repositoryUrl) {
		this.repositoryUrl = repositoryUrl;
	}

	@Property(name = "Git Credentials", description = "The credentials to be used for logging in to the git repository.")
	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	@Override
	protected void shutDown() throws Exception {
		git.peek().ifPresent(Git::close);
		super.shutDown();
	}

	private Instant getEarliestPointToTakeIntoAccount() {
		return Optional.ofNullable(getLookBack())
				.map(ta -> ZonedDateTime.now(ZoneId.of("UTC")).minus(ta))
				.map(ChronoZonedDateTime::toInstant)
				.orElse(null);
	}

	protected Git getGit() {
		return git.get();
	}

	protected synchronized <R> R streamLog(Function<Stream<RevCommit>, R> function)
			throws NoHeadException, GitAPIException, IOException {
		return function.apply(streamLog());
	}

	protected Predicate<RevCommit> isCommitAfter(Instant from) {
		if (from != null) {
			return between(r -> Instant.ofEpochSecond(r.getCommitTime()), from, null);
		} else {
			return x -> true;
		}
	}

	protected <T> Predicate<T> between(@Nonnull Function<T, Instant> getTimeFunction,
			@Nonnull Instant from,
			@Nullable Instant to) {
		return r -> {
			Instant time = getTimeFunction.apply(r);//Instant.ofEpochSecond(r.getCommitTime());
			return (null == to || time.isBefore(to)) && time.isAfter(from);
		};
	}

	private synchronized Stream<RevCommit> streamLog() throws NoHeadException, GitAPIException, IOException {
		fetch();
		return StreamSupport//
				.stream(getGit()//
						.log()
						.all()
						.call()
						.spliterator(), true)
				.filter(isCommitAfter(getEarliestPointToTakeIntoAccount()));
	}

	private synchronized void fetch() {
		try {
			getLogger().info("Pulling updates of GIT repository [{}]",
					getGit().getRepository().getDirectory().getAbsolutePath());
			withCredentials(getGit().fetch()).call();
			PullResult pullResult = withCredentials(getGit().pull()).call();
			getLogger().info("Fetched from [{}]. Result [{}]", pullResult.getFetchedFrom(), pullResult.isSuccessful());
		} catch (GitAPIException e) {
			getLogger().error(String.format("Cannot PULL repository [%s]",
					getGit().getRepository().getDirectory().getAbsolutePath()), e);
		}
	}

	private Git createGitRepository() throws IOException, GitAPIException, URISyntaxException {
		if (getLogger().isDebugEnabled()) {
			getLogger().debug("Creating Git object for: " + getRepositoryUrl());
		}
		if (getRepositoryUrl().contains(":")) {
			final String protocol = getRepositoryUrl().substring(0, getRepositoryUrl().indexOf(":")).toLowerCase();
			switch (protocol) {
				case "http":
				case "https":
				case "git":
					return createRemoteGitRepository(getRepositoryUrl());
				case "file":
					return createLocalGitRepository(getRepositoryUrl());
				default:
			}
		}
		throw new RuntimeException("Cannot recognize url: " + getRepositoryUrl());
	}

	private Git createLocalGitRepository(String repositoryUrl) throws IOException {
		getLogger().info("Creating local repository [{}]: ", repositoryUrl);
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repo = builder.setGitDir(new File(repositoryUrl)).setMustExist(true).build();
		return new Git(repo);
	}

	private Git createRemoteGitRepository(String repositoryUrl)
			throws GitAPIException, IOException, URISyntaxException {
		getLogger().info("Creating remote repository [{}] ", repositoryUrl);
		// Create temp-directory
		final Path tempDir = Files.createTempDirectory("oversigt-temp-git");
		FileUtils.deleteFolderOnExit(tempDir);

		// Clone repo to temp dir
		getLogger().debug("Start cloning");
		final CloneCommand cloneCommand = Git.cloneRepository();
		cloneCommand.setDirectory(tempDir.toFile());
		cloneCommand.setURI(repositoryUrl);
		cloneCommand.setCredentialsProvider(createCredentialsProvider(tempDir));

		// Create repo
		final Git git = cloneCommand.call();
		getLogger().debug("Cloning done.");
		return git;
	}

	private <T, Y, C extends GitCommand<Y>, X extends TransportCommand<C, T>> X withCredentials(X command) {
		command.setCredentialsProvider(createCredentialsProvider(getGit().getRepository().getDirectory()));
		return command;
	}

	private CredentialsProvider createCredentialsProvider(File localRepositoryPath) {
		return createCredentialsProvider(localRepositoryPath.toPath());
	}

	private CredentialsProvider createCredentialsProvider(Path localRepositoryPath) {
		final Path gitPath = localRepositoryPath.getFileName().toString().equals(".git")
				? localRepositoryPath
				: localRepositoryPath.resolve(".git");
		return new CredentialsProvider() {
			@Override
			public boolean supports(CredentialItem... items) {
				return isUsernamePassword(items) || isSkipSslTrust(items);
			}

			@Override
			public boolean isInteractive() {
				return false;
			}

			@Override
			public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
				if (isUsernamePassword(items)) {
					for (CredentialItem item : items) {
						if (item instanceof Username) {
							((Username) item).setValue(getCredentials().getUsername());
						} else if (item instanceof Password) {
							((Password) item).setValue(getCredentials().getPassword().toCharArray());
						}
					}
					return true;
				} else if (!isCheckSSL() && isSkipSslTrust(items)) {
					// JGitText.get().sslTrustForNow
					String message = MessageFormat.format(JGitText.get().sslTrustForRepo,
							gitPath.toAbsolutePath().toString());
					for (CredentialItem item : items) {
						if (item instanceof YesNoType //
								&& item.getPromptText().equals(message)) {
							((YesNoType) item).setValue(true);
						}
					}
					return true;
				}
				return false;
			}

			private boolean isSkipSslTrust(CredentialItem[] items) {
				return items.length == 4 //
						&& items[0] instanceof InformationalMessage //
						&& items[1] instanceof YesNoType //
						&& items[2] instanceof YesNoType //
						&& items[3] instanceof YesNoType //
						&& items[0].getPromptText().endsWith(JGitText.get().sslFailureTrustExplanation);
			}

			private boolean isUsernamePassword(CredentialItem[] items) {
				return items.length == 2//
						&& (items[0] instanceof Username//
								&& items[1] instanceof Password//
								|| items[0] instanceof Password//
										&& items[1] instanceof Username);
			}
		};
	}
}
