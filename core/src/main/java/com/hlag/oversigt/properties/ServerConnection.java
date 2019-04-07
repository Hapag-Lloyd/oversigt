package com.hlag.oversigt.properties;

import java.net.MalformedURLException;
import java.net.URL;

import com.hlag.oversigt.properties.SerializableProperty.Description;

import de.larssh.utils.SneakyException;

@Description("Details for a server connection containing a hostname and a port.")
public class ServerConnection extends SerializableProperty {
	public static final ServerConnection EMPTY = new ServerConnection(0, "", "");

	@Member(icon = "cloud", size = 6)
	private String url;

	public ServerConnection(final int id, final String name, final String url) {
		super(id, name);
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	private URL createUrl() {
		try {
			return new URL(url);
		} catch (final MalformedURLException e) {
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
