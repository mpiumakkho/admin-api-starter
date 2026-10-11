package com.adminstarter.accesscontrol.notification.service;

import com.adminstarter.accesscontrol.AccessControlProperties;
import jakarta.mail.MessagingException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends the emails over SMTP (spring.mail.*; Mailpit in development) from the Thymeleaf templates in
 * templates/mail. A failure is logged, not thrown: the change that asked for the email has already committed, and
 * the person can be sent the link again.
 */
@Service
public class MailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationService.class);
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final JavaMailSender mail;
    private final ITemplateEngine templates;
    private final String from;
    private final String productName;

    public MailNotificationService(JavaMailSender mail, ITemplateEngine templates, AccessControlProperties properties) {
        this.mail = mail;
        this.templates = templates;
        this.from = properties.mail().from();
        this.productName = properties.productName();
    }

    @Async
    @Override
    public void sendInvitation(String email, String firstName, String link, Instant expiresAt) {
        send(email, "You're invited to " + productName, "mail/invite", firstName, link, expiresAt);
    }

    @Async
    @Override
    public void sendPasswordReset(String email, String firstName, String link, Instant expiresAt) {
        send(email, "Set a new password for " + productName, "mail/password-reset", firstName, link, expiresAt);
    }

    @Async
    @Override
    public void sendNotice(String email, String firstName, String subject, List<String> lines) {
        var context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("lines", lines);
        context.setVariable("productName", productName);
        send(email, subject, "mail/notice", context);
    }

    private void send(String to, String subject, String template, String firstName, String link, Instant expiresAt) {
        var context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("link", link);
        context.setVariable("expiresAt", EXPIRY.format(expiresAt));
        context.setVariable("productName", productName);
        send(to, subject, template, context);
    }

    private void send(String to, String subject, String template, Context context) {
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
