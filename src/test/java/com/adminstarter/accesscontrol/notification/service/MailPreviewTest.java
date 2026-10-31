package com.adminstarter.accesscontrol.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adminstarter.accesscontrol.AccessControlProperties.Mail.Theme;
import com.adminstarter.accesscontrol.notification.service.NotificationService.Recipient;
import com.adminstarter.accesscontrol.notification.service.NotificationService.Text;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Not a test: every email in every theme and both languages, to compare the themes (P-044). Skipped unless asked for:
 * {@code ./mvnw test -Dtest=MailPreviewTest -Dmail.preview=files} writes them to target/mail-preview, with an
 * index.html that shows them side by side, and {@code -Dmail.preview=localhost:1025} also sends them to that SMTP
 * server, such as Mailpit from docker/docker-compose.yml, each to {theme}.{language}@preview.test.
 */
@EnabledIfSystemProperty(named = "mail.preview", matches = ".+")
class MailPreviewTest {

    private static final Instant NOW = Instant.parse("2026-10-12T07:30:00Z");
    private static final String URL = "https://admin.example.com";

    private record Email(String name, BiConsumer<MailNotificationService, Recipient> send) {}

    private static final List<Email> EMAILS = List.of(
            new Email("invite", (service, to) -> service.sendInvitation(to, URL + "/activate?token=preview", NOW.plus(Duration.ofDays(7)))),
            new Email("password-reset", (service, to) -> service.sendPasswordReset(to, URL + "/reset-password?token=preview",
                    NOW.plus(Duration.ofHours(1)))),
            new Email("new-device", (service, to) -> service.sendNotice(to, "new-device",
                    List.of("Chrome 131", "Windows 11", "203.0.113.24", NOW))),
            new Email("role-given", (service, to) -> service.sendNotice(to, "role-given", List.of("Manager",
                    Text.of("mail.role-given.scope.branch", "Chiang Mai"), "", Text.of("mail.role-given.until", NOW.plus(Duration.ofDays(90)))))),
            new Email("email-changed", (service, to) -> service.sendNotice(to, "email-changed",
                    List.of("ann@example.com", "ann.lee@example.com"))),
            new Email("password-changed", (service, to) -> service.sendNotice(to, "password-changed", List.of())),
            new Email("inactive-suspended", (service, to) -> service.sendNotice(to, "inactive-suspended", List.of(90))),
            new Email("access-review", (service, to) -> service.sendNotice(to, "access-review",
                    List.of("Q4 2026", 12, NOW.plus(Duration.ofDays(14)), URL + "/access-reviews/1"))));

    @Test
    void every_email_in_every_theme() throws Exception {
        var target = System.getProperty("mail.preview");
        var smtp = new JavaMailSenderImpl();
        if (!"files".equals(target)) {
            smtp.setHost(target.substring(0, target.indexOf(':')));
            smtp.setPort(Integer.parseInt(target.substring(target.indexOf(':') + 1)));
        }
        var folder = Path.of("target", "mail-preview");
        Files.createDirectories(folder);
        var index = new StringBuilder("""
                <!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><title>Email themes</title><style>
                body { margin: 0; padding: 24px; background: #e5e7eb; font: 14px system-ui, sans-serif; color: #111827; }
                h2 { margin: 32px 0 12px; } .row { display: flex; gap: 16px; overflow-x: auto; padding-bottom: 8px; }
                figure { margin: 0; flex: none; } figcaption { margin-bottom: 6px; color: #4b5563; }
                iframe { width: 640px; height: 860px; border: 0; border-radius: 8px; background: #fff; }
                </style></head><body><h1>Email themes (access-control.mail.theme)</h1>
                """);
        for (var theme : Theme.values()) {
            index.append("<h2>").append(theme.template()).append("</h2><div class=\"row\">");
            for (var language : List.of("th", "en")) {
                for (var email : EMAILS) {
                    var file = theme.template() + "-" + email.name() + "-" + language + ".html";
                    var mail = mock(JavaMailSender.class);
                    when(mail.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
                    doAnswer(call -> {
                        MimeMessage message = call.getArgument(0);
                        Files.writeString(folder.resolve(file), (String) message.getContent());
                        if (!"files".equals(target)) {
                            smtp.send(message);
                        }
                        return null;
                    }).when(mail).send(any(MimeMessage.class));
                    var to = new Recipient(theme.template() + "." + language + "@preview.test", "th".equals(language) ? "สมชาย" : "Ann",
                            Locale.forLanguageTag(language));
                    email.send().accept(MailNotificationServiceTest.service(mail, theme), to);
                    index.append("<figure><figcaption>").append(email.name()).append(" · ").append(language)
                            .append("</figcaption><iframe src=\"").append(file).append("\"></iframe></figure>");
                }
            }
            index.append("</div>");
        }
        Files.writeString(folder.resolve("index.html"), index.append("</body></html>"));
    }
}
