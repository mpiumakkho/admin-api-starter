package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.adminstarter.accesscontrol.notification.service.NotificationService.Recipient;
import com.adminstarter.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** "The language of emails" in API.md (P-029): each person's language, or the settings' one until it is known */
class EmailLanguageIT extends ApiIntegrationTest {

    @Test
    void an_invitation_is_in_the_language_picked_for_the_new_person_or_the_settings_one() {
        var admin = admin();
        var viewer = roleId(admin, "Viewer");
        var english = uniqueEmail();
        var draft = new HashMap<>(draft(english, viewer, "invited"));
        draft.put("language", "en");
        assertThat(body(send(HttpMethod.POST, "/api/users", admin, draft)).get("language").asString()).isEqualTo("en");
        verify(notifications).sendInvitation(in(english, "en"), any(), any(Instant.class));

        // Thai, the settings' language on a new installation
        var thai = uniqueEmail();
        assertThat(body(send(HttpMethod.POST, "/api/users", admin, draft(thai, viewer, "invited"))).get("language").isNull()).isTrue();
        verify(notifications).sendInvitation(in(thai, "th"), any(), any(Instant.class));

        var odd = new HashMap<>(draft(uniqueEmail(), viewer, "invited"));
        odd.put("language", "fr");
        assertRefused(send(HttpMethod.POST, "/api/users", admin, odd), HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @Test
    void people_keep_their_own_language_which_the_log_leaves_out() {
        var user = newUser("Viewer", "viewer-pass-1");
        var path = "/api/users/" + user.userId();
        assertThat(body(send(HttpMethod.PATCH, path, user, Map.of("language", "en"))).get("language").asString()).isEqualTo("en");
        assertRefused(send(HttpMethod.PATCH, path, user, Map.of("language", "fr")), HttpStatus.BAD_REQUEST, "invalid_request");
        assertThat(body(get("/api/activity", admin())).valueStream()
                .filter(entry -> entry.get("action").asString().equals("user.updated")
                        && entry.get("targetId").asString().equals(user.userId().toString())))
                .isEmpty();

        // A reset link they ask for then comes in English
        var email = user.user().get("email").asString();
        assertThat(mvc.post().uri("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))).exchange()).hasStatus(HttpStatus.ACCEPTED);
        verify(notifications).sendPasswordReset(in(email, "en"), any(), any(Instant.class));

        // Null gives the settings' language back
        assertThat(body(send(HttpMethod.PATCH, path, user, "{\"language\":null}")).get("language").isNull()).isTrue();
    }

    @Test
    void the_settings_language_is_one_the_emails_are_written_in_and_its_change_is_logged() {
        var admin = admin();
        assertThat(body(get("/api/settings", admin)).get("defaultLanguage").asString()).isEqualTo("th");
        assertRefused(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("defaultLanguage", "fr")),
                HttpStatus.BAD_REQUEST, "invalid_request");
        try {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("defaultLanguage", "en"))).hasStatusOk();
            var entry = body(get("/api/activity", admin)).valueStream()
                    .filter(item -> item.get("action").asString().equals("settings.updated")).findFirst().orElseThrow();
            var change = entry.get("changes").get(0);
            assertThat(change.get("field").asString()).isEqualTo("defaultLanguage");
            assertThat(change.get("from").asString()).isEqualTo("th");
            assertThat(change.get("to").asString()).isEqualTo("en");
        } finally {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("defaultLanguage", "th"))).hasStatusOk();
        }
    }

    /** An email to this address, written in this language */
    private static Recipient in(String email, String language) {
        return argThat(to -> to != null && to.email().equals(email) && to.locale().getLanguage().equals(language));
    }
}
