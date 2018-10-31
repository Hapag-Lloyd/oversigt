package com.hlag.oversigt.properties;

import java.net.InetSocketAddress;
import java.net.Proxy.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;

public class HttpProxy extends SerializableProperty {
	public static final HttpProxy EMPTY = new HttpProxy(0, "", "", 0);

	@Member(icon = "hdd", size = 4)
	private String hostname;
	@Member(icon = "ellipsis", size = 2)
	private int port;

	public HttpProxy(int id, String name, String host, int port) {
		super(id, name);
		this.hostname = host;
		this.port = port;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	@JsonIgnore
	public java.net.Proxy getProxy() {
		if (Strings.isNullOrEmpty(getHostname())) {
			return java.net.Proxy.NO_PROXY;
		} else {
			return new java.net.Proxy(Type.HTTP, new InetSocketAddress(getHostname(), getPort()));
		}
	}
}
