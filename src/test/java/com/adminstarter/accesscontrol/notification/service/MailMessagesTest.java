package com.adminstarter.accesscontrol.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** The text of the emails (P-029): both languages say every message, and each kind of notice has its subject and body */
class MailMessagesTest {

    /** The kinds the listeners send */
    private static final List<String> KINDS =
            List.of("new-device", "role-given", "email-changed", "password-changed", "inactive-suspended", "access-review");

    @Test
    void thai_has_every_message_english_has_and_nothing_else() throws IOException {
        assertThat(load("i18n/mail_th.properties").stringPropertyNames()).isEqualTo(load("i18n/mail.properties").stringPropertyNames());
    }

    @Test
    void each_kind_of_notice_has_a_subject_and_a_body() throws IOException {
        var english = load("i18n/mail.properties");
        for (var kind : KINDS) {
            assertThat(english.getProperty("mail." + kind + ".subject")).as(kind).isNotBlank();
            assertThat(english.getProperty("mail." + kind + ".body")).as(kind).isNotBlank();
        }
    }

    private static Properties load(String path) throws IOException {
        var properties = new Properties();
        try (var reader = new InputStreamReader(
                Objects.requireNonNull(MailMessagesTest.class.getClassLoader().getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
