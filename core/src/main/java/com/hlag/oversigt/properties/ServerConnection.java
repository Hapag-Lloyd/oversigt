package com.hlag.oversigt.properties;

import java.net.MalformedURLException;
import java.net.URL;

import com.hlag.oversigt.properties.SerializableProperty.Description;
import com.hlag.oversigt.util.SneakyException;

@Description("Describes the connection to a server allowing to reuse the connection details in multiple event sources.")
public class ServerConnection extends SerializableProperty {
	public static final ServerConnection EMPTY = new ServerConnection(0, "", "");

	@Member(icon = "cloud", size = 6)
	private String url;

	public ServerConnection(int id, String name, String url) {
		super(id, name);
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	private URL createUrl() {
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			throw new SneakyException(e);
		}
	}

	public String extractHostname() {
		return createUrl().getHost();
	}

	public int extractPort() {
		return createUrl().getPort();
	}
}
