package com.hlag.oversigt.connect.ssh;

import java.util.Properties;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

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

		@Override
		public String getPassphrase() {
			return null;
		}

		@Override
		public String getPassword() {
			return password;
		}

		@Override
		public boolean promptPassphrase(final String arg0) {
			return false;
		}

		@Override
		public boolean promptPassword(final String arg0) {
			return true;
		}

		@Override
		public boolean promptYesNo(final String arg0) {
			return false;
		}

		@Override
		public void showMessage(final String arg0) {}
	}
}
