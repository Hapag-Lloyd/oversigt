package com.hlag.oversigt.connect.ssh;

import com.hlag.oversigt.util.Utils;

import edu.umd.cs.findbugs.annotations.Nullable;

class SshConnectionKey {
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

	@Override
	public int hashCode() {
		return Utils.computeHashCode(hostname, port, username);
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
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
		if (!hostname.equals(other.hostname)) {
			return false;
		}
		if (port != other.port) {
			return false;
		}
		if (!username.equals(other.username)) {
			return false;
		}
		return true;
	}
}
