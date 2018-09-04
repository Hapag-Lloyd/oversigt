package com.hlag.oversigt.connect.ssh;

import java.util.Properties;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

class SshConnectionWithPassword extends SshConnection {
	private final char[] password;

	SshConnectionWithPassword(String hostname, int port, String username, String password) {
		super(hostname, port, username);
		this.password = password.toCharArray();
	}

	@Override
	protected synchronized Session createSession(JSch jsch) throws JSchException {
		Session session = jsch.getSession(getUsername(), getHostname(), getPort());
		Properties properties = new java.util.Properties();
		properties.put("StrictHostKeyChecking", "no");
		session.setDaemonThread(true);
		session.setConfig(properties);
		session.setUserInfo(new FixedUserInfo(password));
		session.setTimeout(10000);
		session.connect();
		return session;
	}

	private static class FixedUserInfo implements UserInfo {

		private final String password;

		private FixedUserInfo(char[] password) {
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
		public boolean promptPassphrase(String arg0) {
			return false;
		}

		@Override
		public boolean promptPassword(String arg0) {
			return true;
		}

		@Override
		public boolean promptYesNo(String arg0) {
			return false;
		}

		@Override
		public void showMessage(String arg0) {
		}
	}
}
