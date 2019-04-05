package com.hlag.oversigt.connect.ssh;

import java.util.Comparator;

class SshConnectionKey implements Comparable<SshConnectionKey> {
	@SuppressWarnings("rawtypes")
	protected static final Comparator COMPARATOR = //
			Comparator.comparing(SshConnectionKey::getHostname)
					.thenComparing(Comparator.comparingInt(SshConnectionKey::getPort))
					.thenComparing(Comparator.comparing(SshConnectionKey::getUsername));

	private final String hostname;

	private final int port;

	private final String username;

	protected SshConnectionKey(final String hostname, final int port, final String username) {
		this.hostname = hostname;
		this.port = port;
		this.username = username;
	}

	protected String getHostname() {
		return hostname;
	}

	protected int getPort() {
		return port;
	}

	protected String getUsername() {
		return username;
	}

	@SuppressWarnings("unchecked")
	@Override
	public int compareTo(final SshConnectionKey that) {
		return COMPARATOR.compare(this, that);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (hostname == null ? 0 : hostname.hashCode());
		result = prime * result + port;
		result = prime * result + (username == null ? 0 : username.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final SshConnectionKey other = (SshConnectionKey) obj;
		if (hostname == null) {
			if (other.hostname != null) {
				return false;
			}
		} else if (!hostname.equals(other.hostname)) {
			return false;
		}
		if (port != other.port) {
			return false;
		}
		if (username == null) {
			if (other.username != null) {
				return false;
			}
		} else if (!username.equals(other.username)) {
			return false;
		}
		return true;
	}
}
