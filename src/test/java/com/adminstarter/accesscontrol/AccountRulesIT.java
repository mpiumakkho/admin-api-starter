package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.identity.service.TotpService;
import com.adminstarter.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/**
 * The account rules in the settings (API.md, "Settings", "Roles that must use two-step sign-in"). The database is
 * shared by every test class, so each test puts back the settings it changes.
 */
class AccountRulesIT extends ApiIntegrationTest {

    @Autowired
    TotpService totp;

    @Test
    void the_account_rules_start_at_their_defaults_and_stay_within_their_ranges() {
        var admin = admin();
        var settings = body(get("/api/settings", admin));
        assertThat(settings.get("twoFactorRoleIds").isEmpty()).isTrue();
        assertThat(settings.get("passwordMinLength").asInt()).isEqualTo(8);
        assertThat(settings.get("passwordHistory").asInt()).isZero();
        assertThat(settings.get("activityRetentionDays").asInt()).isEqualTo(730);

        for (var outside : List.<Map<String, Object>>of(
                Map.of("passwordMinLength", 7), Map.of("passwordMinLength", 65),
                Map.of("passwordHistory", -1), Map.of("passwordHistory", 25),
                Map.of("activityRetentionDays", 29), Map.of("activityRetentionDays", 3651),
                Map.of("twoFactorRoleIds", List.of("not-a-role-id")))) {
            assertRefused(send(HttpMethod.PATCH, "/api/settings", admin, outside), HttpStatus.BAD_REQUEST, "invalid_request");
        }
        assertRefused(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("twoFactorRoleIds", List.of(UUID.randomUUID()))),
                HttpStatus.NOT_FOUND, "not_found");
        assertThat(body(get("/api/settings", admin))).isEqualTo(settings);
    }

    @Test
    void two_factor_roles_are_kept_without_duplicates_and_logged_as_added_and_removed() {
        var admin = admin();
        var role = id(newRole(admin, List.of()));
        withSettings(admin, Map.of("twoFactorRoleIds", List.of(role, role), "passwordMinLength", 12), () -> {
            var settings = body(get("/api/settings", admin));
            assertThat(strings(settings.get("twoFactorRoleIds"))).containsExactly(role);
            assertThat(settings.get("passwordMinLength").asInt()).isEqualTo(12);

            var entry = body(get("/api/activity", admin)).valueStream()
                    .filter(candidate -> candidate.get("action").asString().equals("settings.updated"))
                    .findFirst().orElseThrow();
            var changes = entry.get("changes").valueStream().toList();
            assertThat(changes).anySatisfy(change -> {
                assertThat(change.get("field").asString()).isEqualTo("passwordMinLength");
                assertThat(change.get("from").asString()).isEqualTo("8");
                assertThat(change.get("to").asString()).isEqualTo("12");
            });
            assertThat(changes).anySatisfy(change -> {
                assertThat(change.get("field").asString()).isEqualTo("twoFactorRoleIds");
                assertThat(strings(change.get("added"))).containsExactly(role);
                assertThat(change.get("removed").isEmpty()).isTrue();
            });
        });
    }

    @Test
    void deleting_a_role_takes_it_off_the_two_factor_roles_unless_the_delete_is_refused() {
        var admin = admin();
        var held = id(newRole(admin, List.of()));
        var spare = id(newRole(admin, List.of()));
        assertThat(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), UUID.fromString(held), "active"))).hasStatusOk();
        withSettings(admin, Map.of("twoFactorRoleIds", List.of(held, spare)), () -> {
            assertRefused(send(HttpMethod.DELETE, "/api/roles/" + held, admin, null), HttpStatus.CONFLICT, "role_in_use");
            assertThat(send(HttpMethod.DELETE, "/api/roles/" + spare, admin, null)).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(strings(body(get("/api/settings", admin)).get("twoFactorRoleIds"))).containsExactly(held);
        });
    }

    // Roles that must use two-step sign-in ---------------------------------------------------------------------------

    @Test
    void a_role_that_requires_two_step_sign_in_holds_back_everything_but_turning_it_on() {
        var admin = admin();
        var role = newRole(admin, List.of("users.view", "roles.view", "activity.view"));
        var user = newUser(role.get("name").asString(), "guarded-pass-1");
        var email = user.user().get("email").asString();
        withSettings(admin, Map.of("twoFactorRoleIds", List.of(id(role))), () -> {
            // Signing in still works; after that, only what turning it on needs
            assertThat(login(email, "guarded-pass-1", null)).hasStatusOk();
            assertRefused(get("/api/users", user), HttpStatus.FORBIDDEN, "two_factor_mandatory");
            assertRefused(get("/api/activity", user), HttpStatus.FORBIDDEN, "two_factor_mandatory");
            assertRefused(send(HttpMethod.PATCH, "/api/users/" + user.userId(), user, Map.of("phone", "1")),
                    HttpStatus.FORBIDDEN, "two_factor_mandatory");
            assertThat(get("/api/me", user)).hasStatusOk();
            assertThat(get("/api/settings", user)).hasStatusOk();
            assertThat(get("/api/roles", user)).hasStatusOk();

            var secret = body(send(HttpMethod.POST, "/api/me/two-factor", user, null)).get("secret").asString();
            assertThat(send(HttpMethod.POST, "/api/me/two-factor/confirm", user, Map.of("code", totp.codeAt(secret, Instant.now()))))
                    .hasStatusOk().bodyJson().extractingPath("$.twoFactor").isEqualTo(true);
            assertThat(get("/api/users", user)).hasStatusOk();
            assertThat(get("/api/activity", user)).hasStatusOk();
            assertRefused(send(HttpMethod.POST, "/api/me/two-factor/disable", user, Map.of("code", totp.codeAt(secret, Instant.now()))),
                    HttpStatus.FORBIDDEN, "two_factor_mandatory");

            // An administrator can still turn it off, for a lost phone; then it has to be turned on again first
            assertThat(send(HttpMethod.POST, "/api/users/" + user.userId() + "/two-factor/reset", admin, null))
                    .hasStatusOk().bodyJson().extractingPath("$.twoFactor").isEqualTo(false);
            assertRefused(get("/api/users", user), HttpStatus.FORBIDDEN, "two_factor_mandatory");
            assertThat(get("/api/me", user)).hasStatusOk().bodyJson().extractingPath("$.twoFactor").isEqualTo(false);
        });
        // Once the role no longer requires it, the same token gets through again
        assertThat(get("/api/users", user)).hasStatusOk();
    }

    /** A new role granting these permissions */
    protected JsonNode newRole(Browser admin, List<String> permissions) {
        var role = send(HttpMethod.POST, "/api/roles", admin, Map.of("name", "Rule " + UUID.randomUUID(), "permissions", permissions));
        assertThat(role).hasStatusOk();
        return body(role);
    }

    /** Runs {@code test} with these settings, then puts back what they were */
    protected void withSettings(Browser admin, Map<String, Object> changes, Runnable test) {
        var before = body(get("/api/settings", admin));
        var restore = new LinkedHashMap<String, Object>();
        changes.keySet().forEach(field -> restore.put(field, before.get(field)));
        assertThat(send(HttpMethod.PATCH, "/api/settings", admin, changes)).hasStatusOk();
        try {
            test.run();
        } finally {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin, restore)).hasStatusOk();
        }
    }

    protected static String id(JsonNode node) {
        return node.get("id").asString();
    }

    protected static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
