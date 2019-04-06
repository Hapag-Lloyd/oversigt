package com.hlag.oversigt.util;

import static com.hlag.oversigt.util.Utils.logChange;
import static com.hlag.oversigt.util.Utils.map;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ForkJoinPool;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hlag.oversigt.model.Dashboard;
import com.hlag.oversigt.security.Authenticator;
import com.hlag.oversigt.security.Principal;
import com.hlag.oversigt.security.Roles;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

@Singleton
public class MailSender {
	private static final Logger LOGGER = LoggerFactory.getLogger(MailSender.class);

	@Inject
	private Configuration templateConfiguration;

	@Inject
	private Authenticator authenticator;

	@Inject
	@Named("hostname")
	private String dashboardHostname;

	@Inject
	@Named("serverAdmins")
	private List<String> adminUserIds;

	@Inject
	@Named("mailSenderHost")
	private String host;

	@Inject
	@Named("mailSenderPort")
	private int port;

	@Inject
	@Named("mailSenderStartTls")
	private boolean startTls;

	@Inject
	@Named("mailSenderUsername")
	private String username;

	@Inject
	@Named("mailSenderPassword")
	private String password;

	@Inject
	@Named("mailSenderAddress")
	private String senderAddress;

	public void sendNewDashboard(final Principal sender, final Dashboard dashboard) {
		sendMailToAdmins(sender,
				"New dashboard requested",
				"A new dashboard has been created...",
				"NewDashboard",
				map("dashboard", dashboard));
	}

	public void sendPermissionsReceived(final Principal sender,
			final Collection<String> receiverUserId,
			final Roles role,
			final Dashboard dashboard) {
		receiverUserId.forEach(receiver -> sendPermissionsReceived(sender, receiver, role, dashboard));
	}

	public void sendPermissionsReceived(final Principal sender,
			final String receiverUserId,
			final Roles role,
			final Dashboard dashboard) {
		sendMailToUserId(sender,
				receiverUserId,
				"New permission received",
				"You have received a new permssion for dashboard '" + dashboard.getTitle() + "'.",
				"PermissionReceived",
				map("role", role.getDisplayName(), "dashboard", dashboard));
	}

	public void sendDashboardEnabled(final Principal sender, final Dashboard dashboard) {
		sendMailToDashboardInvolved(sender,
				dashboard,
				"Dashboard enabled",
				"The dashboard '" + dashboard.getTitle() + "' has been enabled. Configure it now!",
				"DashboardEnabled",
				map("dashboard", dashboard));
	}

	private void sendMailToAdmins(final Principal sender,
			final String subject,
			final String title,
			final String templatePath,
			final Map<String, Object> model) {
		sendMailToUserIds(sender, adminUserIds, subject, title, templatePath, model);
	}

	private void sendMailToDashboardInvolved(final Principal sender,
			final Dashboard dashboard,
			final String subject,
			final String title,
			final String templatePath,
			final Map<String, Object> model) {
		final List<String> involved = new ArrayList<>();
		involved.addAll(dashboard.getOwners());
		involved.addAll(dashboard.getEditors());
		model.put("dashboard", dashboard);
		sendMailToUserIds(sender, involved, subject, title, templatePath, model);
	}

	private void sendMailToUserIds(final Principal sender,
			final List<String> userIds,
			final String subject,
			final String title,
			final String templatePath,
			final Map<String, Object> model) {
		userIds.forEach(userId -> sendMailToUserId(sender, userId, subject, title, templatePath, model));
	}

	private void sendMailToUserId(final Principal sender,
			final String userId,
			final String subject,
			final String title,
			final String templatePath,
			final Map<String, Object> model) {
		final Principal receiver = authenticator.readPrincipal(userId);
		sendMail(sender, receiver.getName(), receiver.getEmail(), subject, title, templatePath, model);
	}

	private void sendMail(final Principal sender,
			final String recipientName,
			final String recipientEmail,
			final String subject,
			final String title,
			final String templatePath,
			final Map<String, Object> mModel) {
		Objects.requireNonNull(recipientName, "The recipient name must not be null");
		Objects.requireNonNull(recipientEmail, "The recipient mail must not be null");

		final HashMap<String, Object> model = new HashMap<>(mModel);
		model.put("hostname", dashboardHostname);
		model.put("subject", subject);
		model.put("title", title);
		model.put("recipient", map("name", recipientName, "email", recipientEmail));

		if (!model.containsKey("preHeaderText")) {
			model.put("preHeaderText", title);
		}

		ForkJoinPool.commonPool().execute(() -> {
			try {
				sendMail_unsafe(sender, new String[] { recipientEmail }, subject, templatePath, model);
			} catch (final IOException | TemplateException e) {
				LOGGER.error("Unable to send mail.", e);
			}
		});
	}

	/**
	 * DO NOT USE THIS METHOD IF YOU ARE WRITING YOUR OWN SEND-MAIL METHOD!<br/>
	 * This method generates the mail content and then sends the mail to the given
	 * recipients
	 *
	 * @param recipients   all mail addresses that shall receive this current mail
	 * @param subject      the subject of the mail to send
	 * @param templateName the name to the template file to be used
	 * @param model        the model to be used to fill the template
	 * @return <code>true</code> if mail sending was successful, otherwise
	 *         <code>false</code>
	 * @throws IOException       if any IO error occurres - reading a file or
	 *                           sending a mail
	 * @throws TemplateException if something fails while processing the template
	 */
	private void sendMail_unsafe(final Principal sender,
			final String[] recipients,
			final String subject,
			final String templateName,
			final Map<String, Object> model) throws IOException, TemplateException {
		final String templatePath = "mails/" + templateName + ".ftl";
		final Template htmlTemplate = templateConfiguration.getTemplate(templatePath + ".html");
		final StringWriter html = new StringWriter();
		htmlTemplate.process(model, html);

		final Template textTemplate = templateConfiguration.getTemplate(templatePath + ".txt");
		final StringWriter text = new StringWriter();
		textTemplate.process(model, text);

		send_internal(sender, recipients, subject, html.toString(), text.toString());
	}

	/**
	 * DO NOT USE THIS METHOD IF YOU ARE WRITING YOUR OWN SEND-MAIL METHOD!<br/>
	 * This method sends a mail with the given subject and contents to the given
	 * recipients.
	 *
	 * @param recipients  all mail addresses that shall receive the current mail
	 * @param subject     the subject of the mail to send
	 * @param htmlContent the HTML content of the file to send
	 * @param textContent the text content of the file to send
	 * @return <code>true</code> if mail sending was successful, otherwise
	 *         <code>false</code>
	 */
	private void send_internal(final Principal sender,
			final String[] recipients,
			final String subject,
			final String htmlContent,
			final String textContent) {
		if (Objects.requireNonNull(recipients).length == 0) {
			throw new RuntimeException("No recipient given");
		}

		if (Strings.isNullOrEmpty(host)) {
			LOGGER.warn("Mailer hostname is not set. Mailing is deactivated.");
			return;
		}
		if (port <= 0) {
			LOGGER.warn("Mailer port has an invalid value. Mailing is deactivated.");
			return;
		}
		if (Strings.isNullOrEmpty(senderAddress)) {
			LOGGER.warn("Mailer sender address is not set. Mailing is deactivated.");
			return;
		}

		final Session session = Session.getInstance(createProperties(), createAuthenticator());

		try {
			final Message message = new MimeMessage(session);
			for (final String recipient : recipients) {
				message.addRecipient(RecipientType.TO, new InternetAddress(recipient));
			}
			message.setFrom(new InternetAddress(sender.getEmail(), sender.getName()));
			message.setSubject(subject);
			message.setSentDate(new Date());

			final Multipart multipart = new MimeMultipart("alternative");
			final BodyPart textBodyPart = new MimeBodyPart();
			textBodyPart.setContent(textContent, "text/plain");
			final BodyPart htmlBodyPart = new MimeBodyPart();
			htmlBodyPart.setContent(htmlContent, "text/html");

			multipart.addBodyPart(textBodyPart);
			multipart.addBodyPart(htmlBodyPart);
			message.setContent(multipart);

			Transport.send(message);

			logChange(sender,
					"Sent mail to %s with subject '%s'. HTML size %s and TXT size %s",
					Arrays.toString(recipients),
					subject,
					htmlContent.length(),
					textContent.length());
		} catch (final MessagingException | UnsupportedEncodingException e) {
			throw new RuntimeException("Unable to send mail", e);
		}
	}

	private javax.mail.Authenticator createAuthenticator() {
		if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
			return new javax.mail.Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, password);
				}
			};
		} else {
			return null;
		}
	}

	private Properties createProperties() {
		final Properties config = new Properties();
		config.put("mail.smtp.auth",
				Boolean.toString(!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)));
		config.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
		config.put("mail.smtp.host", host);
		config.put("mail.smtp.port", Integer.toString(port));
		return config;
	}
}
