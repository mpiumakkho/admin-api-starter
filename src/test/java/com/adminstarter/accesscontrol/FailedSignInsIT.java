package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.identity.service.TotpService;
import com.adminstarter.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** Failed sign-ins and locked accounts in the activity log (API.md, "Signing in"; P-026) */
class FailedSignInsIT extends ApiIntegrationTest {

    private static final String PASSWORD = "viewer-pass-1";

    @Autowired
    TotpService totp;

    @Test
    void a_refused_sign_in_of_someone_who_exists_is_logged_with_why() {
        var user = newUser("Viewer", PASSWORD);
        var email = user.user().get("email").asString();

        assertRefused(login(email, "not-the-password", null), HttpStatus.UNAUTHORIZED, "invalid_credentials");
        var failed = failures(user).getFirst();
        assertThat(failed.get("actorId").asString()).isEqualTo(user.userId().toString());
        assertThat(cause(failed)).isEqualTo("password");

        // A wrong code, with two-step sign-in on
        var secret = body(send(HttpMethod.POST, "/api/me/two-factor", user, null)).get("secret").asString();
        send(HttpMethod.POST, "/api/me/two-factor/confirm", user, Map.of("code", totp.codeAt(secret, Instant.now())));
        assertRefused(login(email, PASSWORD, "000000"), HttpStatus.UNAUTHORIZED, "invalid_code");
        assertThat(cause(failures(user).getFirst())).isEqualTo("code");

        // Confirming who you are with a wrong password
        assertRefused(send(HttpMethod.POST, "/api/me/reauthenticate", user, Map.of("password", "not-the-password")),
                HttpStatus.UNAUTHORIZED, "invalid_credentials");
        assertThat(cause(failures(user).getFirst())).isEqualTo("password");

        // Suspended: the right password, still refused
        assertThat(send(HttpMethod.POST, "/api/users/status", admin(),
                Map.of("ids", List.of(user.userId().toString()), "status", "suspended", "reason", "On leave"))).hasStatusOk();
        assertRefused(login(email, PASSWORD, null), HttpStatus.FORBIDDEN, "account_suspended");
        assertThat(cause(failures(user).getFirst())).isEqualTo("suspended");
        assertThat(failures(user)).hasSize(4);
    }

    @Test
    void an_email_that_belongs_to_nobody_is_not_logged() {
        var before = entries("auth.sign_in_failed").size();
        assertRefused(login("nobody-" + UUID.randomUUID() + "@example.com", "whatever-1", null), HttpStatus.UNAUTHORIZED,
                "invalid_credentials");
        assertThat(entries("auth.sign_in_failed")).hasSize(before);
    }

    @Test
    void too_many_wrong_passwords_lock_the_account_and_the_log_says_so() {
        var user = newUser("Viewer", PASSWORD);
        var email = user.user().get("email").asString();
        for (var attempt = 0; attempt < 5; attempt++) {
            assertRefused(login(email, "not-the-password", null), HttpStatus.UNAUTHORIZED, "invalid_credentials");
        }
        var locked = entries("auth.account_locked").stream()
                .filter(entry -> entry.get("targetId").asString().equals(user.userId().toString())).toList();
        assertThat(locked).hasSize(1);
        assertThat(locked.getFirst().get("changes").get(0).get("field").asString()).isEqualTo("lockMinutes");
        assertThat(locked.getFirst().get("changes").get(0).get("to").asString()).isEqualTo("1");

        // Locked: the right password is refused too, and logged as such
        assertRefused(login(email, PASSWORD, null), HttpStatus.UNAUTHORIZED, "invalid_credentials");
        assertThat(cause(failures(user).getFirst())).isEqualTo("locked");
    }

    /** The failed sign-ins of this person, newest first */
    private List<JsonNode> failures(Browser user) {
        return entries("auth.sign_in_failed").stream()
                .filter(entry -> entry.get("targetId").asString().equals(user.userId().toString())).toList();
    }

    private List<JsonNode> entries(String action) {
        return body(get("/api/activity", admin())).valueStream()
                .filter(entry -> entry.get("action").asString().equals(action)).toList();
    }

    private static String cause(JsonNode entry) {
        return entry.get("changes").get(0).get("to").asString();
    }
}
