package com.adminstarter.accesscontrol.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adminstarter.accesscontrol.AccessControlProperties.Mail.Theme;
import com.adminstarter.accesscontrol.notification.service.NotificationService.Recipient;
import com.adminstarter.accesscontrol.notification.service.NotificationService.Text;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** The emails as people read them (P-029): the templates and messages, in Thai and in English, with Bangkok time */
class MailNotificationServiceTest {

    private static final Locale THAI = Locale.forLanguageTag("th");
    private static final Instant EXPIRES = Instant.parse("2026-10-12T07:30:00Z");

    private JavaMailSender mail;
    private MailNotificationService service;

    @BeforeEach
    void service() {
        mail = mock(JavaMailSender.class);
        when(mail.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        service = service(mail, Theme.INDIGO);
    }

    /** The service with the real templates and messages, sending through this sender */
    static MailNotificationService service(JavaMailSender mail, Theme theme) {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/mail");
        messages.setDefaultEncoding("UTF-8");
        messages.setAlwaysUseMessageFormat(true);
        messages.setFallbackToSystemLocale(false);
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        var templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);
        templates.setTemplateEngineMessageSource(messages);
        return new MailNotificationService(mail, templates, messages, "no-reply@example.com", "Admin", ZoneId.of("Asia/Bangkok"),
                theme);
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    void every_theme_lays_out_a_link_with_its_button_its_note_and_the_link_written_out(Theme theme) throws Exception {
        service(mail, theme).sendPasswordReset(new Recipient("somchai@example.com", "สมชาย", THAI),
                "https://admin.example.com/reset-password?token=t", EXPIRES);

        var html = (String) sent().getContent();
        assertThat(html).contains("lang=\"th\"", "<title>ตั้งรหัสผ่านใหม่สำหรับ Admin</title>", "รหัสผ่าน", "เรียน คุณสมชาย",
                "มีคำขอตั้งรหัสผ่านใหม่", "href=\"https://admin.example.com/reset-password?token=t\"", ">ตั้งรหัสผ่านใหม่<",
                "12 ต.ค. 2569 เวลา 14:30 น. (GMT+7)", "หากกดปุ่มไม่ได้", "หากคุณไม่ได้ขอ", "อีเมลนี้ส่งจาก Admin โดยอัตโนมัติ")
                .doesNotContain(" th:", "${");
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    void every_theme_lays_out_a_notice_with_a_paragraph_a_line_and_no_button(Theme theme) throws Exception {
        service(mail, theme).sendNotice(new Recipient("ann@example.com", "Ann", Locale.ENGLISH), "inactive-suspended", List.of(90));

        var html = (String) sent().getContent();
        assertThat(html).contains("Account status", "Hi Ann,", "nobody signed in to it for 90 days.",
                "Ask your administrator to restore it", "If you did not expect this").doesNotContain("<a ", " th:");
    }

    @Test
    void a_notice_whose_kind_has_a_button_takes_its_link_from_the_values() throws Exception {
        service.sendNotice(new Recipient("ann@example.com", "Ann", Locale.ENGLISH), "access-review",
                List.of("Q4", 3, EXPIRES, "https://admin.example.com/access-reviews/1"));

        assertThat((String) sent().getContent()).contains("Action needed",
                "href=\"https://admin.example.com/access-reviews/1\"", ">Open the review</a>").doesNotContain("Open the review:");
    }

    @Test
    void an_invitation_in_thai_says_when_the_link_runs_out_in_thai_time_and_the_buddhist_year() throws Exception {
        service.sendInvitation(new Recipient("somchai@example.com", "สมชาย", THAI), "https://admin.example.com/activate?token=t", EXPIRES);

        var sent = sent();
        assertThat(sent.getSubject()).isEqualTo("คุณได้รับเชิญให้ใช้ Admin");
        var html = (String) sent.getContent();
        assertThat(html).contains("lang=\"th\"", "เรียน คุณสมชาย", "ตั้งรหัสผ่าน", "https://admin.example.com/activate?token=t",
                "12 ต.ค. 2569 เวลา 14:30 น. (GMT+7)");
    }

    @Test
    void a_notice_in_english_writes_its_values_in_english_too() throws Exception {
        service.sendNotice(new Recipient("ann@example.com", "Ann", Locale.ENGLISH), "role-given", List.of("Manager",
                Text.of("mail.role-given.scope.branch", "Chiang Mai"), "", Text.of("mail.role-given.until", EXPIRES)));

        var sent = sent();
        assertThat(sent.getSubject()).isEqualTo("You have a new role in Admin");
        assertThat((String) sent.getContent()).contains("Hi Ann,",
                "You were given the role Manager in Chiang Mai only, until 12 Oct 2026, 14:30 (GMT+7).",
                "If you did not expect this, tell your administrator, and change your password.");
    }

    @Test
    void a_kind_with_a_footer_of_its_own_says_it_instead() throws Exception {
        service.sendNotice(new Recipient("ann@example.com", "Ann", THAI), "access-review",
                List.of("ไตรมาส 4", 3, EXPIRES, "https://admin.example.com/access-reviews/1"));

        var html = (String) sent().getContent();
        assertThat(html).contains("รอบทบทวนสิทธิ์ ไตรมาส 4 มีการมอบบทบาท 3 รายการ", "คุณได้รับอีเมลนี้เพราะ")
                .doesNotContain("เปลี่ยนรหัสผ่าน");
    }

    @Test
    void a_language_without_messages_of_its_own_gets_english() {
        assertThat(service.text(Locale.JAPANESE, "mail.password-changed.subject", List.of())).isEqualTo("Your Admin password was changed");
    }

    private MimeMessage sent() {
        var message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(message.capture());
        return message.getValue();
    }
}
