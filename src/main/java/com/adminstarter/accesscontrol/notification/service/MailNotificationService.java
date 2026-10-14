package com.adminstarter.accesscontrol.notification.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import jakarta.mail.MessagingException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends the emails over SMTP (spring.mail.*; Mailpit in development) from the Thymeleaf templates in templates/mail and
 * the messages in i18n/mail*.properties, in the reader's language (P-029). Dates and times are in the mail time zone,
 * with the Buddhist year in Thai. A failure is logged, not thrown: the change that asked for the email has already
 * committed, and the person can be sent the link again.
 */
@Service
public class MailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationService.class);

    private final JavaMailSender mail;
    private final ITemplateEngine templates;
    private final MessageSource messages;
    private final String from;
    private final String productName;
    private final ZoneId zone;

    @Autowired
    public MailNotificationService(JavaMailSender mail, ITemplateEngine templates, MessageSource messages,
            AccessControlProperties properties) {
        this(mail, templates, messages, properties.mail().from(), properties.productName(), ZoneId.of(properties.timeZone()));
    }

    MailNotificationService(JavaMailSender mail, ITemplateEngine templates, MessageSource messages, String from,
            String productName, ZoneId zone) {
        this.mail = mail;
        this.templates = templates;
        this.messages = messages;
        this.from = from;
        this.productName = productName;
        this.zone = zone;
    }

    @Async
    @Override
    public void sendInvitation(Recipient to, String link, Instant expiresAt) {
        sendLink(to, "mail.invite.subject", "mail/invite", link, expiresAt);
    }

    @Async
    @Override
    public void sendPasswordReset(Recipient to, String link, Instant expiresAt) {
        sendLink(to, "mail.reset.subject", "mail/password-reset", link, expiresAt);
    }

    @Async
    @Override
    public void sendNotice(Recipient to, String kind, List<Object> values) {
        var locale = to.locale();
        var context = context(to, text(locale, "mail." + kind + ".subject", values));
        context.setVariable("lines", List.of(text(locale, "mail." + kind + ".body", values).split("\n")));
        var footer = messages.getMessage("mail." + kind + ".footer", new Object[] {productName}, "", locale);
        context.setVariable("footer", footer == null || footer.isEmpty() ? text(locale, "mail.notice.footer", List.of()) : footer);
        send(to.email(), "mail/notice", context);
    }

    /** A message in this language: the product's name as {0}, then the values, each written in the language too */
    String text(Locale locale, String key, List<Object> values) {
        var args = new ArrayList<Object>(values.size() + 1);
        args.add(productName);
        for (var value : values) {
            args.add(switch (value) {
                case Text text -> text(locale, text.key(), text.values());
                case Instant at -> time(at, locale);
                default -> value;
            });
        }
        return messages.getMessage(key, args.toArray(), locale);
    }

    /** "12 Oct 2026, 14:05 (GMT+7)"; in Thai "12 ต.ค. 2569 เวลา 14:05 น. (GMT+7)", with the Buddhist year */
    String time(Instant at, Locale locale) {
        if ("th".equals(locale.getLanguage())) {
            return DateTimeFormatter.ofPattern("d MMM yyyy 'เวลา' HH:mm 'น.' (O)", locale)
                    .withChronology(ThaiBuddhistChronology.INSTANCE).withZone(zone).format(at);
        }
        return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm (O)", Locale.UK).withZone(zone).format(at);
    }

    private void sendLink(Recipient to, String subject, String template, String link, Instant expiresAt) {
        var context = context(to, text(to.locale(), subject, List.of()));
        context.setVariable("link", link);
        context.setVariable("expiresAt", time(expiresAt, to.locale()));
        send(to.email(), template, context);
    }

    private Context context(Recipient to, String subject) {
        var context = new Context(to.locale());
        context.setVariable("firstName", to.firstName());
        context.setVariable("productName", productName);
        context.setVariable("subject", subject);
        return context;
    }

    private void send(String to, String template, Context context) {
        var subject = String.valueOf(context.getVariable("subject"));
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(templates.process(template, context), true);
            mail.send(message);
        } catch (MailException | MessagingException e) {
            log.error("Could not send \"{}\" to {}", subject, to, e);
        }
    }
}
