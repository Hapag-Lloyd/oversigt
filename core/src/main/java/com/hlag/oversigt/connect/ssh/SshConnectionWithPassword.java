package com.hlag.oversigt.connect.ssh;

import java.util.Properties;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import edu.umd.cs.findbugs.annotations.Nullable;

class SshConnectionWithPassword extends SshConnection {
	private final char[] password;

	SshConnectionWithPassword(final String hostname, final int port, final String username, final String password) {
		super(hostname, port, username);
		this.password = password.toCharArray();
	}

	@Override
	protected synchronized Session createSession(final JSch jsch) throws JSchException {
		final Session session = jsch.getSession(getUsername(), getHostname(), getPort());
		final Properties properties = new java.util.Properties();
		properties.put("StrictHostKeyChecking", "no");
		session.setDaemonThread(true);
		session.setConfig(properties);
		session.setUserInfo(new FixedUserInfo(password));
		session.setTimeout(10000);
		session.connect();
		return session;
	}

	private static final class FixedUserInfo implements UserInfo {

		private final String password;

		private FixedUserInfo(final char[] password) {
			this.password = new String(password);
		}

		/**
		 * This method should never be called because {@link #promptPassword(String)}
		 * returns <code>false</code>.
		 */
		@Override
		public String getPassphrase() {
			return "";
		}

		@Override
		public String getPassword() {
			return password;
		}

		@Override
		public boolean promptPassphrase(@SuppressWarnings("unused") @Nullable final String arg0) {
			return false;
		}

		@Override
		public boolean promptPassword(@SuppressWarnings("unused") @Nullable final String arg0) {
			return true;
		}

		@Override
		public boolean promptYesNo(@SuppressWarnings("unused") @Nullable final String arg0) {
			return false;
		}

		@Override
		public void showMessage(@SuppressWarnings("unused") @Nullable final String arg0) {
			// empty by design
		}
	}
}
