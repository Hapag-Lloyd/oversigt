package com.hlag.oversigt.connect.exchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import microsoft.exchange.webservices.data.core.exception.service.local.ServiceLocalException;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.property.complex.EmailAddress;

public final class Mail {
	private static final Logger LOGGER = LoggerFactory.getLogger(Mail.class);

	static Optional<Mail> create(final EmailMessage emailMessage) {
		try {
			return Optional.of(new Mail(emailMessage));
		} catch (final ServiceLocalException e) {
			LOGGER.error("Unable to create mail", e);
			return Optional.empty();
		}
	}

	private final String id;

	private final String subject;

	private final String fromAddress;

	private final String fromName;

	private final Map<String, String> tos = new HashMap<>();

	private final Map<String, String> ccs = new HashMap<>();

	private final boolean isRead;

	private final boolean hasAttachment;

	private final List<String> categories = new ArrayList<>();

	private Mail(final EmailMessage mail) throws ServiceLocalException {
		id = mail.getId().getUniqueId();
		subject = mail.getSubject();

		final EmailAddress from = mail.getFrom();
		fromAddress = from.getAddress();
		fromName = from.getName();

		final Iterator<EmailAddress> toIter = mail.getToRecipients().iterator();
		while (toIter.hasNext()) {
			final EmailAddress to = toIter.next();
			tos.put(to.getName(), to.getAddress());
		}

		final Iterator<EmailAddress> ccIter = mail.getCcRecipients().iterator();
		while (ccIter.hasNext()) {
			final EmailAddress cc = ccIter.next();
			ccs.put(cc.getName(), cc.getAddress());
		}

		isRead = mail.getIsRead();
		hasAttachment = mail.getHasAttachments();

		final Iterator<String> catIter = mail.getCategories().iterator();
		while (catIter.hasNext()) {
			categories.add(catIter.next());
		}
	}

	public String getId() {
		return id;
	}

	public String getSubject() {
		return subject;
	}

	public String getFromAddress() {
		return fromAddress;
	}

	public String getFromName() {
		return fromName;
	}

	public Map<String, String> getTos() {
		return tos;
	}

	public Map<String, String> getCcs() {
		return ccs;
	}

	public boolean isRead() {
		return isRead;
	}

	public boolean isHasAttachment() {
		return hasAttachment;
	}

	public List<String> getCategories() {
		return categories;
	}
}
