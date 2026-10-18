package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.adminstarter.accesscontrol.identity.service.InactiveAccountJob;
import com.adminstarter.accesscontrol.notification.service.NotificationService.Text;
import com.adminstarter.support.ApiIntegrationTest;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
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
        verify(notifications).sendNotice(to(email(idle)), eq("inactive-suspended"), eq(List.of(90)));
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
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin(), Map.of("sessionTimeoutMinutes", 30))).hasStatusOk();
        }
    }

    @Test
    void a_session_ends_its_lifetime_after_sign_in_however_often_it_refreshes() {
        var user = newUser("Viewer", PASSWORD);
        var session = sessionId(user);
        // As if it signed in 11 hours 50 minutes ago and kept refreshing: ten minutes left of its 12 hours
        jdbc.update("update access_control.user_sessions set created_at = created_at - interval '11 hours 50 minutes', "
                + "expires_at = expires_at - interval '11 hours 50 minutes' where id = ?", session);
        jdbc.update("update access_control.refresh_tokens set expires_at = expires_at - interval '11 hours 50 minutes' "
                + "where session_id = ?", session);
        var ends = jdbc.queryForObject("select expires_at from access_control.user_sessions where id = ?", Timestamp.class, session);

        var refreshed = refresh(user.cookie());
        assertThat(refreshed).hasStatusOk();
        // Refreshing gave it no more time, nor its new cookie
        assertThat(jdbc.queryForObject("select expires_at from access_control.user_sessions where id = ?", Timestamp.class, session))
                .isEqualTo(ends);
        assertThat(jdbc.queryForObject("select max(expires_at) from access_control.refresh_tokens where session_id = ?",
                Timestamp.class, session)).isEqualTo(ends);

        // Once its time is over, the cookie buys nothing, and the person signs in again
        // An hour ago, so a database clock a little off the server's does not matter
        jdbc.update("update access_control.user_sessions set expires_at = now() - interval '1 hour' where id = ?", session);
        assertThat(refresh(refreshed.getResponse().getCookie(COOKIE))).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/me", user)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void a_new_installation_signs_people_out_after_half_an_hour_without_activity() {
        assertThat(get("/api/settings", admin())).hasStatusOk().bodyJson().extractingPath("$.sessionTimeoutMinutes").isEqualTo(30);
    }

    @Test
    void people_hear_by_email_about_a_new_device_a_role_given_and_their_email_or_password_changed() {
        var admin = admin();
        var user = newUser("Viewer", PASSWORD);
        var email = email(user);
        // The first password, from the invitation, says nothing
        verify(notifications, never()).sendNotice(to(email), eq("password-changed"), anyList());

        // The same browser again says nothing; another one does
        signIn(email, PASSWORD);
        verify(notifications, never()).sendNotice(to(email), eq("new-device"), anyList());
        assertThat(mvc.post().uri("/api/auth/login").header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "remember", false))).exchange())
                .hasStatusOk();
        verify(notifications).sendNotice(to(email), eq("new-device"),
                argThat(values -> values.get(0).equals("Firefox") && values.get(1).equals("Linux")));

        assertThat(send(HttpMethod.POST, "/api/role-assignments", admin,
                Map.of("userId", user.userId().toString(), "roleId", roleId(admin, "Manager").toString()))).hasStatusOk();
        verify(notifications).sendNotice(to(email), eq("role-given"), eq(List.of("Manager",
                Text.of("mail.role-given.scope.organization"), "", Text.of("mail.role-given.for-good"))));

        assertThat(send(HttpMethod.POST, "/api/me/password", user,
                Map.of("currentPassword", PASSWORD, "newPassword", "another-pass-2"))).hasStatus(HttpStatus.NO_CONTENT);
        verify(notifications, atLeastOnce()).sendNotice(to(email), eq("password-changed"), anyList());

        // To the old address
        var changed = uniqueEmail();
        assertThat(send(HttpMethod.PATCH, "/api/users/" + user.userId(), admin(), Map.of("email", changed))).hasStatusOk();
        verify(notifications).sendNotice(to(email), eq("email-changed"), eq(List.of(email, changed)));
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
        jdbc.update("update access_control.user_sessions set last_seen_at = now() - make_interval(mins => ?) where id = ?",
                minutes, sessionId(as));
    }

    /** The session of this browser's access token */
    private UUID sessionId(Browser as) {
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(as.token().split("\\.")[1]));
        return UUID.fromString(claims.get("sid").asString());
    }
}
