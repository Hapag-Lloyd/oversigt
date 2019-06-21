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

/**
 * Lightweight representation of an email message
 *
 * @author neumaol
 *
 */
public final class Mail {
	private static final Logger LOGGER = LoggerFactory.getLogger(Mail.class);

	/**
	 * Create a lightweight representation of an email message
	 *
	 * @param emailMessage the original data to copy
	 * @return the created mail message or an empty {@link Optional} of it was not
	 *         possible to create the object.
	 */
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

	/**
	 * @return the logger
	 */
	public static Logger getLogger() {
		return LOGGER;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the subject
	 */
	public String getSubject() {
		return subject;
	}

	/**
	 * @return the fromAddress
	 */
	public String getFromAddress() {
		return fromAddress;
	}

	/**
	 * @return the fromName
	 */
	public String getFromName() {
		return fromName;
	}

	/**
	 * @return the tos
	 */
	public Map<String, String> getTos() {
		return tos;
	}

	/**
	 * @return the ccs
	 */
	public Map<String, String> getCcs() {
		return ccs;
	}

	/**
	 * @return the isRead
	 */
	public boolean isRead() {
		return isRead;
	}

	/**
	 * @return the hasAttachment
	 */
	public boolean isHasAttachment() {
		return hasAttachment;
	}

	/**
	 * @return the categories
	 */
	public List<String> getCategories() {
		return categories;
	}

}
