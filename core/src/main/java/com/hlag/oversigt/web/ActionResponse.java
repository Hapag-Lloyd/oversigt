package com.hlag.oversigt.web;

import io.undertow.util.StatusCodes;

class ActionResponse {
	public static ActionResponse ok() {
		return doGetRedirect();
	}

	public static ActionResponse serverError(String text) {
		return new ActionResponse(false, false, StatusCodes.INTERNAL_SERVER_ERROR, text, null);
	}

	public static ActionResponse doGetRedirect() {
		return new ActionResponse(true, false, null, null, null);
	}

	public static ActionResponse okJson(Object object) {
		return new ActionResponse(false, false, StatusCodes.OK, object, null);
	}

	public static ActionResponse redirect(String url) {
		return new ActionResponse(false, false, null, null, url);
	}

	private final boolean doGetRedirect;
	private final boolean doNoAction;
	private final Integer statusCode;
	private final Object jsonObject;
	private final String redirect;

	ActionResponse() {
		this(false, true, null, null, null);
	}

	private ActionResponse(boolean doGetRedirect,
			boolean doNoAction,
			Integer statusCode,
			Object jsonObject,
			String redirect) {
		this.doGetRedirect = doGetRedirect;
		this.doNoAction = doNoAction;
		this.statusCode = statusCode;
		this.jsonObject = jsonObject;
		this.redirect = redirect;
	}

	public Object getJsonObject() {
		return jsonObject;
	}

	public String getRedirect() {
		return redirect;
	}

	public Integer getStatusCode() {
		return statusCode;
	}

	public boolean isDoGetRedirect() {
		return doGetRedirect;
	}

	public boolean isDoNoAction() {
		return doNoAction;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append(getStatusCode()).append(" ").append("\n");
		if (getRedirect() != null) {
			sb.append("Location: ").append(getRedirect()).append("\n");
		}
		if (getJsonObject() != null) {
			sb.append("\n").append(getJsonObject().toString());
		}

		return sb.toString();
	}
}
