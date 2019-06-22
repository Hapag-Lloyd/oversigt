package com.hlag.oversigt.connect.exchange;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

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
		} catch (final ServiceLocalException | AddressException e) {
			LOGGER.error("Unable to create mail", e);
			return Optional.empty();
		}
	}

	private final String id;

	private final String subject;

	private final String fromAddress;

	private final String fromName;

	private final Map<InternetAddress, String> tos;

	private final Map<InternetAddress, String> ccs;

	private final boolean isRead;

	private final boolean hasAttachment;

	private final List<String> categories;

	private Mail(final EmailMessage mail) throws ServiceLocalException, AddressException {
		id = mail.getId().getUniqueId();
		subject = mail.getSubject();

		final EmailAddress from = mail.getFrom();
		fromAddress = from.getAddress();
		fromName = from.getName();

		final Map<InternetAddress, String> tos = new LinkedHashMap<>();
		for (final EmailAddress to : mail.getToRecipients()) {
			tos.put(new InternetAddress(to.getAddress()), to.getName());
		}
		this.tos = unmodifiableMap(tos);

		final Map<InternetAddress, String> ccs = new LinkedHashMap<>();
		for (final EmailAddress to : mail.getCcRecipients()) {
			ccs.put(new InternetAddress(to.getAddress()), to.getName());
		}
		this.ccs = unmodifiableMap(ccs);

		isRead = mail.getIsRead();
		hasAttachment = mail.getHasAttachments();
		categories = unmodifiableList(Lists.newArrayList(mail.getCategories()));
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
	public Map<InternetAddress, String> getTos() {
		return tos;
	}

	/**
	 * @return the ccs
	 */
	public Map<InternetAddress, String> getCcs() {
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
