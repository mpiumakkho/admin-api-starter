package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.adminstarter.accesscontrol.identity.service.InactiveAccountJob;
import com.adminstarter.support.ApiIntegrationTest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Accounts nobody uses, sessions left idle, and the emails people get about their account (API.md, "Emails about your
 * account" and "Settings"; P-027)
 */
class AccountHygieneIT extends ApiIntegrationTest {

    private static final String PASSWORD = "viewer-pass-1";

    @Autowired
    InactiveAccountJob inactiveAccounts;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void the_nightly_job_suspends_accounts_nobody_used_for_the_settings_days() {
        var idle = newUser("Viewer", PASSWORD);
        var busy = newUser("Viewer", PASSWORD);
        jdbc.update("update access_control.users set last_active_at = now() - interval '100 days' where id = ?", idle.userId());
        try {
            inactiveAccounts.run();
            assertThat(status(idle)).as("off while the setting is 0").isEqualTo("active");
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin(), Map.of("suspendInactiveDays", 90))).hasStatusOk();
            inactiveAccounts.run();
        } finally {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin(), Map.of("suspendInactiveDays", 0))).hasStatusOk();
        }

        assertThat(status(idle)).isEqualTo("suspended");
        assertThat(status(busy)).isEqualTo("active");
        // Their sessions ended with it
        assertThat(get("/api/me", idle)).hasStatus(HttpStatus.UNAUTHORIZED);
        var entry = body(get("/api/activity", admin())).valueStream()
                .filter(item -> item.get("action").asString().equals("user.suspended")
                        && item.get("targetId").asString().equals(idle.userId().toString()))
                .findFirst().orElseThrow();
        assertThat(entry.get("actorId").isNull()).isTrue();
        assertThat(entry.get("changes").valueStream().map(change -> change.get("field").asString() + "=" + change.get("to").asString()))
                .contains("status=suspended", "inactiveDays=90");
        verify(notifications).sendNotice(eq(email(idle)), any(), endsWith("account was suspended"), anyList());
    }

    @Test
    void a_session_with_no_request_for_the_idle_timeout_ends_on_the_server_too() {
        var user = newUser("Viewer", PASSWORD);
        var other = signIn(email(user), PASSWORD);
        try {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin(), Map.of("sessionTimeoutMinutes", 15))).hasStatusOk();
            assertThat(get("/api/me", user)).hasStatusOk();

            lastSeenAgo(user, 20);
            assertThat(get("/api/me", user)).hasStatus(HttpStatus.UNAUTHORIZED);
            // Nor does the refresh cookie buy a new token
            lastSeenAgo(other, 20);
            assertThat(refresh(other.cookie())).hasStatus(HttpStatus.UNAUTHORIZED);
        } finally {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin(), Map.of("sessionTimeoutMinutes", 0))).hasStatusOk();
        }
    }

    @Test
    void people_hear_by_email_about_a_new_device_a_role_given_and_their_email_or_password_changed() {
        var admin = admin();
        var user = newUser("Viewer", PASSWORD);
        var email = email(user);
        // The first password, from the invitation, says nothing
        verify(notifications, never()).sendNotice(eq(email), any(), endsWith("password was changed"), anyList());

        // The same browser again says nothing; another one does
        signIn(email, PASSWORD);
        verify(notifications, never()).sendNotice(eq(email), any(), startsWith("New sign-in"), anyList());
        assertThat(mvc.post().uri("/api/auth/login").header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "remember", false))).exchange())
                .hasStatusOk();
        verify(notifications).sendNotice(eq(email), any(), startsWith("New sign-in"),
                argThat(lines -> lines.get(1).contains("Firefox on Linux")));

        assertThat(send(HttpMethod.POST, "/api/role-assignments", admin,
                Map.of("userId", user.userId().toString(), "roleId", roleId(admin, "Manager").toString()))).hasStatusOk();
        verify(notifications).sendNotice(eq(email), any(), startsWith("You have a new role"),
                argThat(lines -> lines.get(0).contains("Manager for the whole organization, for good")));

        assertThat(send(HttpMethod.POST, "/api/me/password", user,
                Map.of("currentPassword", PASSWORD, "newPassword", "another-pass-2"))).hasStatus(HttpStatus.NO_CONTENT);
        verify(notifications, atLeastOnce()).sendNotice(eq(email), any(), endsWith("password was changed"), anyList());

        // To the old address
        var changed = uniqueEmail();
        assertThat(send(HttpMethod.PATCH, "/api/users/" + user.userId(), admin(), Map.of("email", changed))).hasStatusOk();
        verify(notifications).sendNotice(eq(email), any(), endsWith("email was changed"), argThat(lines -> lines.get(0).contains(changed)));
    }

    private String status(Browser user) {
        return body(get("/api/users", admin())).valueStream()
                .filter(item -> item.get("id").asString().equals(user.userId().toString()))
                .map(item -> item.get("status").asString()).findFirst().orElseThrow();
    }

    private static String email(Browser user) {
        return user.user().get("email").asString();
    }

    /** As if this browser made its last request so many minutes ago */
    private void lastSeenAgo(Browser as, int minutes) {
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(as.token().split("\\.")[1]));
        jdbc.update("update access_control.user_sessions set last_seen_at = now() - make_interval(mins => ?) where id = ?",
                minutes, UUID.fromString(claims.get("sid").asString()));
    }
}
