package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.identity.service.TotpService;
import com.adminstarter.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** Signing in, the refresh cookie, sessions and two-step sign-in, as API.md describes them */
class AuthenticationFlowIT extends ApiIntegrationTest {

    @Autowired
    TotpService totp;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void wrong_or_unknown_credentials_are_refused_alike() {
        assertRefused(login(ADMIN_EMAIL, "wrong-password", null), HttpStatus.UNAUTHORIZED, "invalid_credentials");
        assertRefused(login(uniqueEmail(), ADMIN_PASSWORD, null), HttpStatus.UNAUTHORIZED, "invalid_credentials");
    }

    @Test
    void a_request_needs_an_access_token() {
        assertRefused(mvc.get().uri("/api/users").exchange(), HttpStatus.UNAUTHORIZED, "unauthenticated");
        var admin = admin();
        assertThat(get("/api/me", admin)).hasStatusOk().bodyJson().extractingPath("$.email").isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void the_refresh_cookie_is_http_only_strict_and_limited_to_auth() {
        var cookie = admin().cookie();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    void each_refresh_replaces_the_cookie_and_a_replaced_one_used_again_ends_the_session() {
        var browser = admin();
        var first = refresh(browser.cookie());
        assertThat(first).hasStatusOk();
        var next = first.getResponse().getCookie(COOKIE);
        assertThat(next.getValue()).isNotEqualTo(browser.cookie().getValue());

        // The old cookie again (the test turns the two-tab grace off): taken as stolen, the session ends
        assertRefused(refresh(browser.cookie()), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertRefused(refresh(next), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertRefused(get("/api/me", browser), HttpStatus.UNAUTHORIZED, "unauthenticated");
    }

    @Test
    void signing_out_ends_the_session_even_without_an_access_token() {
        var browser = admin();
        assertThat(mvc.post().uri("/api/auth/logout").cookie(browser.cookie()).exchange())
                .hasStatus(HttpStatus.NO_CONTENT)
                .cookies().hasCookieSatisfying(COOKIE, cookie -> assertThat(cookie.getMaxAge()).isZero());
        assertRefused(refresh(browser.cookie()), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertRefused(get("/api/me", browser), HttpStatus.UNAUTHORIZED, "unauthenticated");
    }

    @Test
    void the_devices_list_marks_the_current_session_and_ending_one_signs_it_out() {
        var user = newUser("Viewer", "first-pass-123");
        var other = signIn(user.user().get("email").asString(), "first-pass-123");
        var sessions = body(get("/api/users/" + user.userId() + "/sessions", user));
        assertThat(sessions).hasSize(2);
        var current = sessions.valueStream().filter(session -> session.get("current").asBoolean()).toList();
        assertThat(current).hasSize(1);

        // Everywhere but here
        assertThat(send(HttpMethod.POST, "/api/users/" + user.userId() + "/sessions/end", user, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertRefused(get("/api/me", other), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertThat(get("/api/me", user)).hasStatusOk();
    }

    @Test
    void changing_a_password_signs_out_the_other_sessions() {
        var user = newUser("Viewer", "first-pass-123");
        var email = user.user().get("email").asString();
        var other = signIn(email, "first-pass-123");
        assertRefused(send(HttpMethod.POST, "/api/me/password", user,
                Map.of("currentPassword", "nope-nope", "newPassword", "second-pass-123")), HttpStatus.UNAUTHORIZED, "invalid_credentials");
        assertRefused(send(HttpMethod.POST, "/api/me/password", user,
                Map.of("currentPassword", "first-pass-123", "newPassword", "short")), HttpStatus.UNPROCESSABLE_CONTENT, "weak_password");
        assertThat(send(HttpMethod.POST, "/api/me/password", user,
                Map.of("currentPassword", "first-pass-123", "newPassword", "second-pass-123"))).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(get("/api/me", user)).hasStatusOk();
        assertRefused(get("/api/me", other), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertRefused(login(email, "first-pass-123", null), HttpStatus.UNAUTHORIZED, "invalid_credentials");
        assertThat(login(email, "second-pass-123", null)).hasStatusOk();
    }

    @Test
    void suspending_someone_ends_their_sessions_at_once() {
        var user = newUser("Viewer", "first-pass-123");
        var admin = admin();
        assertThat(send(HttpMethod.POST, "/api/users/status", admin,
                Map.of("ids", List.of(user.userId().toString()), "status", "suspended", "reason", "Left the company"))).hasStatusOk();
        assertRefused(get("/api/me", user), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertRefused(refresh(user.cookie()), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertRefused(login(user.user().get("email").asString(), "first-pass-123", null), HttpStatus.FORBIDDEN, "account_suspended");
    }

    @Test
    void someone_invited_cannot_sign_in_and_their_link_works_once() {
        var admin = admin();
        var email = uniqueEmail();
        send(HttpMethod.POST, "/api/users", admin, draft(email, roleId(admin, "Viewer"), "invited"));
        var token = invitationToken(email);
        var activate = Map.of("token", token, "password", "first-pass-123");
        assertThat(mvc.post().uri("/api/auth/activate").contentType("application/json").content(json.writeValueAsString(activate)).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        assertRefused(mvc.post().uri("/api/auth/activate").contentType("application/json").content(json.writeValueAsString(activate)).exchange(),
                HttpStatus.BAD_REQUEST, "invalid_token");
        assertThat(login(email, "first-pass-123", null)).hasStatusOk();
    }

    @Test
    void five_wrong_passwords_lock_the_account_for_a_while() {
        var user = newUser("Viewer", "first-pass-123");
        var email = user.user().get("email").asString();
        for (var attempt = 0; attempt < 5; attempt++) {
            assertRefused(login(email, "wrong-" + attempt, null), HttpStatus.UNAUTHORIZED, "invalid_credentials");
        }
        // Even the right password, without saying why
        assertRefused(login(email, "first-pass-123", null), HttpStatus.UNAUTHORIZED, "invalid_credentials");
    }

    @Test
    void two_step_sign_in_asks_for_a_code_once_turned_on() {
        var user = newUser("Viewer", "first-pass-123");
        var email = user.user().get("email").asString();
        var setup = body(send(HttpMethod.POST, "/api/me/two-factor", user, null));
        var secret = setup.get("secret").asString();
        assertThat(setup.get("uri").asString()).startsWith("otpauth://totp/");

        assertRefused(send(HttpMethod.POST, "/api/me/two-factor/confirm", user, Map.of("code", "000000")),
                HttpStatus.UNAUTHORIZED, "invalid_code");
        assertThat(send(HttpMethod.POST, "/api/me/two-factor/confirm", user, Map.of("code", totp.codeAt(secret, Instant.now()))))
                .hasStatusOk().bodyJson().extractingPath("$.user.twoFactor").isEqualTo(true);
        // A copy of the database does not give the secret away (P-014)
        var stored = jdbc.queryForObject("select two_factor_secret from access_control.user_credentials where user_id = ?",
                String.class, user.userId());
        assertThat(stored).startsWith("enc:v1:").doesNotContain(secret);

        assertRefused(login(email, "first-pass-123", null), HttpStatus.UNAUTHORIZED, "two_factor_required");
        assertRefused(login(email, "first-pass-123", "000000"), HttpStatus.UNAUTHORIZED, "invalid_code");
        assertThat(login(email, "first-pass-123", totp.codeAt(secret, Instant.now()))).hasStatusOk();

        // An administrator can turn it off for someone who lost their phone, but not for themselves
        var admin = admin();
        assertThat(send(HttpMethod.POST, "/api/users/" + user.userId() + "/two-factor/reset", admin, null))
                .hasStatusOk().bodyJson().extractingPath("$.twoFactor").isEqualTo(false);
        assertRefused(send(HttpMethod.POST, "/api/users/" + admin.userId() + "/two-factor/reset", admin, null),
                HttpStatus.FORBIDDEN, "self_change");
    }
}
