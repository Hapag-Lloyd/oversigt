package com.hlag.oversigt.properties;

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
}
