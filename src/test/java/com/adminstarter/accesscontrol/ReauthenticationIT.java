package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.identity.service.TotpService;
import com.adminstarter.support.ApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** "Confirming who you are" in API.md (P-023): the risky changes need a recent sign-in or a confirmed password */
class ReauthenticationIT extends ApiIntegrationTest {

    @Autowired
    TotpService totp;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void every_risky_change_asks_once_the_sign_in_is_old_and_the_others_do_not() {
        var admin = admin();
        var viewer = newUser("Viewer", "viewer-pass-1");
        var target = viewer.userId().toString();
        var assignment = viewer.user().get("roles").get(0).get("id").asString();
        var viewerRole = roleId(admin, "Viewer");
        var role = body(send(HttpMethod.POST, "/api/roles", admin,
                Map.of("name", "Step-up " + UUID.randomUUID(), "permissions", List.of("users.view")))).get("id").asString();
        var session = body(get("/api/users/" + target + "/sessions", admin)).get(0).get("id").asString();
        signedInLongAgo(admin);

        List.of(
                send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), viewerRole, "invited")),
                send(HttpMethod.POST, "/api/users/import", admin, Map.of("users", List.of(draft(uniqueEmail(), viewerRole, "invited")))),
                send(HttpMethod.POST, "/api/role-assignments", admin, Map.of("userId", target, "roleId", role)),
                send(HttpMethod.PATCH, "/api/role-assignments/" + assignment, admin,
                        Map.of("endsAt", Instant.now().plus(Duration.ofDays(30)).toString())),
                send(HttpMethod.DELETE, "/api/role-assignments/" + assignment, admin, null),
                send(HttpMethod.POST, "/api/role-assignments/batch", admin, Map.of("userIds", List.of(target), "add", Map.of("roleId", role))),
                send(HttpMethod.PATCH, "/api/roles/" + role, admin, Map.of("permissions", List.of("users.view", "activity.view"))),
                send(HttpMethod.DELETE, "/api/roles/" + role, admin, null),
                send(HttpMethod.POST, "/api/users/delete", admin, Map.of("ids", List.of(target), "reason", "Left the company")),
                send(HttpMethod.POST, "/api/users/" + target + "/reset-password", admin, Map.of("reason", "Lost their phone")),
                send(HttpMethod.POST, "/api/users/" + target + "/two-factor/reset", admin, Map.of("reason", "Lost their phone")),
                send(HttpMethod.DELETE, "/api/sessions/" + session, admin, null),
                send(HttpMethod.POST, "/api/users/" + target + "/sessions/end", admin, null),
                send(HttpMethod.PATCH, "/api/settings", admin, Map.of("inviteExpiryDays", 14)),
                // What recovers an account: the email the reset links go to, and the authenticator
                send(HttpMethod.PATCH, "/api/users/" + target, admin, Map.of("email", uniqueEmail())),
                send(HttpMethod.POST, "/api/me/two-factor", admin, null))
                .forEach(result -> assertRefused(result, HttpStatus.FORBIDDEN, "reauth_required"));
        // Nothing changed
        assertThat(get("/api/me", viewer)).hasStatusOk();

        // A role's color, someone's phone or status, and your own devices do not ask
        assertThat(send(HttpMethod.PATCH, "/api/roles/" + role, admin, Map.of("tone", "info"))).hasStatusOk();
        assertThat(send(HttpMethod.PATCH, "/api/users/" + target, admin, Map.of("phone", "0812345678"))).hasStatusOk();
        signedInLongAgo(viewer);
        assertThat(send(HttpMethod.POST, "/api/users/" + target + "/sessions/end", viewer, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(send(HttpMethod.POST, "/api/users/status", admin,
                Map.of("ids", List.of(target), "status", "suspended", "reason", "On leave"))).hasStatusOk();
    }

    @Test
    void the_password_confirms_it_and_a_refresh_does_not() {
        var admin = admin();
        var viewer = newUser("Viewer", "viewer-pass-1");
        var reset = "/api/users/" + viewer.userId() + "/reset-password";
        signedInLongAgo(admin);

        var later = signedIn(refresh(admin.cookie()));
        assertRefused(send(HttpMethod.POST, reset, later, Map.of("reason", "Lost their phone")), HttpStatus.FORBIDDEN, "reauth_required");

        assertRefused(send(HttpMethod.POST, "/api/me/reauthenticate", later, Map.of("password", "not-the-password")),
                HttpStatus.UNAUTHORIZED, "invalid_credentials");
        assertThat(send(HttpMethod.POST, "/api/me/reauthenticate", later, Map.of("password", ADMIN_PASSWORD)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(send(HttpMethod.POST, reset, later, Map.of("reason", "Lost their phone"))).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void with_two_step_sign_in_on_the_code_is_asked_too() {
        var user = newUser("Viewer", "viewer-pass-1");
        var secret = body(send(HttpMethod.POST, "/api/me/two-factor", user, null)).get("secret").asString();
        // With the code of the step before, so this step's code is left for what comes next (P-036)
        assertThat(send(HttpMethod.POST, "/api/me/two-factor/confirm", user, Map.of("code", totp.codeAt(secret, Instant.now().minusSeconds(30)))))
                .hasStatusOk();

        assertRefused(send(HttpMethod.POST, "/api/me/reauthenticate", user, Map.of("password", "viewer-pass-1")),
                HttpStatus.UNAUTHORIZED, "two_factor_required");
        assertRefused(send(HttpMethod.POST, "/api/me/reauthenticate", user, Map.of("password", "viewer-pass-1", "code", "000000")),
                HttpStatus.UNAUTHORIZED, "invalid_code");
        assertThat(send(HttpMethod.POST, "/api/me/reauthenticate", user,
                Map.of("password", "viewer-pass-1", "code", totp.codeAt(secret, Instant.now())))).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void an_authenticator_in_use_is_not_replaced_until_two_step_sign_in_is_turned_off_with_a_code() {
        var user = newUser("Viewer", "viewer-pass-1");
        var secret = body(send(HttpMethod.POST, "/api/me/two-factor", user, null)).get("secret").asString();
        // With the code of the step before, so this step's code is left for what comes next (P-036)
        assertThat(send(HttpMethod.POST, "/api/me/two-factor/confirm", user, Map.of("code", totp.codeAt(secret, Instant.now().minusSeconds(30)))))
                .hasStatusOk();

        // A stolen access token cannot bind the account to another authenticator, nor take new recovery codes
        assertRefused(send(HttpMethod.POST, "/api/me/two-factor", user, null), HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.POST, "/api/me/two-factor/confirm", user, Map.of("code", totp.codeAt(secret, Instant.now()))),
                HttpStatus.BAD_REQUEST, "invalid_request");

        assertThat(send(HttpMethod.POST, "/api/me/two-factor/disable", user, Map.of("code", totp.codeAt(secret, Instant.now()))))
                .hasStatusOk();
        assertThat(send(HttpMethod.POST, "/api/me/two-factor", user, null)).hasStatusOk();
    }

    @Test
    void zero_minutes_never_asks() {
        var admin = admin();
        try {
            assertRefused(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("reauthMinutes", 1441)),
                    HttpStatus.BAD_REQUEST, "invalid_request");
            var settings = body(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("reauthMinutes", 0)));
            assertThat(settings.get("reauthMinutes").asInt()).isZero();
            var change = latest(admin, "settings.updated").get("changes").get(0);
            assertThat(change.get("field").asString()).isEqualTo("reauthMinutes");
            assertThat(change.get("from").asString()).isEqualTo("10");
            assertThat(change.get("to").asString()).isEqualTo("0");

            signedInLongAgo(admin);
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("inviteExpiryDays", 7))).hasStatusOk();
        } finally {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin(), Map.of("reauthMinutes", 10))).hasStatusOk();
        }
    }

    /** As if this browser signed in, or last confirmed its password, a day ago */
    private void signedInLongAgo(Browser as) {
        var claims = json.readTree(Base64.getUrlDecoder().decode(as.token().split("\\.")[1]));
        jdbc.update("update access_control.user_sessions set verified_at = verified_at - interval '1 day' where id = ?",
                UUID.fromString(claims.get("sid").asString()));
    }

    /** The newest entry in the activity log with this action */
    private JsonNode latest(Browser as, String action) {
        return body(get("/api/activity", as)).valueStream()
                .filter(entry -> entry.get("action").asString().equals(action))
                .findFirst().orElseThrow();
    }
}
