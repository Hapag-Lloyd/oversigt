package com.hlag.oversigt.properties;

import java.net.InetSocketAddress;
import java.net.Proxy.Type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.hlag.oversigt.properties.SerializableProperty.Description;

@Description("Details for an HTTP proxy to access other network parts - e.g. the internet.")
public class HttpProxy extends SerializableProperty {
	public static final HttpProxy EMPTY = new HttpProxy(0, "", "", 0);

	@Member(icon = "hdd", size = 4)
	private String hostname;

	@Member(icon = "ellipsis", size = 2)
	private int port;

	@JsonCreator
	public HttpProxy(@JsonProperty("id") final int id,
			@JsonProperty("name") final String name,
			@JsonProperty("host") final String host,
			@JsonProperty("port") final int port) {
		super(id, name);
		hostname = host;
		this.port = port;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(final String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(final int port) {
		this.port = port;
	}

	@JsonIgnore
	public java.net.Proxy getProxy() {
		if (Strings.isNullOrEmpty(getHostname())) {
			return java.net.Proxy.NO_PROXY;
		}
		return new java.net.Proxy(Type.HTTP, new InetSocketAddress(getHostname(), getPort()));
	}
}
