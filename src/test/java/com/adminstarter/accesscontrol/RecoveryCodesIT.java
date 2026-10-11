package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.identity.service.TotpService;
import com.adminstarter.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** "Recovery codes" in API.md (P-025): ten single-use codes, for when the phone is lost */
class RecoveryCodesIT extends ApiIntegrationTest {

    private static final String PASSWORD = "viewer-pass-1";

    @Autowired
    TotpService totp;

    @Test
    void turning_on_two_step_sign_in_gives_ten_codes_each_good_once() {
        var user = newUser("Viewer", PASSWORD);
        var email = user.user().get("email").asString();
        assertRefused(send(HttpMethod.POST, "/api/me/recovery-codes", user, null), HttpStatus.BAD_REQUEST, "invalid_request");

        var codes = turnOn(user);
        assertThat(codes).hasSize(10).doesNotHaveDuplicates().allMatch(code -> code.matches("[a-z2-9]{5}-[a-z2-9]{5}"));
        assertThat(remaining(user)).isEqualTo(10);

        // In place of a code from the app, once; case, spaces and the dash do not matter
        assertThat(login(email, PASSWORD, codes.get(0).toUpperCase(Locale.ROOT).replace("-", " "))).hasStatusOk();
        assertRefused(login(email, PASSWORD, codes.get(0)), HttpStatus.UNAUTHORIZED, "invalid_code");
        assertThat(login(email, PASSWORD, codes.get(1))).hasStatusOk();
        assertThat(remaining(user)).isEqualTo(8);

        var used = latest("auth.recovery_code_used");
        assertThat(used.get("targetId").asString()).isEqualTo(user.userId().toString());
        assertThat(used.get("actorId").asString()).isEqualTo(user.userId().toString());
        assertThat(used.get("changes").get(0).get("field").asString()).isEqualTo("recoveryCodesLeft");
        assertThat(used.get("changes").get(0).get("to").asString()).isEqualTo("8");
    }

    @Test
    void new_codes_replace_the_old_ones_confirm_who_you_are_and_go_when_it_is_turned_off() {
        var user = newUser("Viewer", PASSWORD);
        var email = user.user().get("email").asString();
        var first = turnOn(user);

        var fresh = body(send(HttpMethod.POST, "/api/me/recovery-codes", user, null)).get("codes").valueStream()
                .map(JsonNode::asString).toList();
        assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(first);
        assertThat(latest("auth.recovery_codes_created").get("targetId").asString()).isEqualTo(user.userId().toString());
        assertRefused(login(email, PASSWORD, first.get(0)), HttpStatus.UNAUTHORIZED, "invalid_code");

        // Wherever a code from the app is asked (P-023)
        assertThat(send(HttpMethod.POST, "/api/me/reauthenticate", user, Map.of("password", PASSWORD, "code", fresh.get(0))))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(send(HttpMethod.POST, "/api/me/two-factor/disable", user, Map.of("code", fresh.get(1))))
                .hasStatusOk().bodyJson().extractingPath("$.twoFactor").isEqualTo(false);
        assertThat(remaining(user)).isZero();
    }

    /** Turns two-step sign-in on, answering its first recovery codes */
    private List<String> turnOn(Browser user) {
        var secret = body(send(HttpMethod.POST, "/api/me/two-factor", user, null)).get("secret").asString();
        var confirmed = body(send(HttpMethod.POST, "/api/me/two-factor/confirm", user, Map.of("code", totp.codeAt(secret, Instant.now()))));
        assertThat(confirmed.get("user").get("twoFactor").asBoolean()).isTrue();
        return confirmed.get("recoveryCodes").valueStream().map(JsonNode::asString).toList();
    }

    private int remaining(Browser user) {
        return body(get("/api/me/recovery-codes", user)).get("remaining").asInt();
    }

    /** The newest entry in the activity log with this action, as an administrator sees it */
    private JsonNode latest(String action) {
        return body(get("/api/activity", admin())).valueStream()
                .filter(entry -> entry.get("action").asString().equals(action))
                .findFirst().orElseThrow();
    }
}
