package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.support.ApiIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/**
 * The account rules in the settings (API.md, "Settings"). The database is shared by every test class, so each test
 * puts back the settings it changes.
 */
class AccountRulesIT extends ApiIntegrationTest {

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
        var role = newRole(admin, List.of());
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
        var held = newRole(admin, List.of());
        var spare = newRole(admin, List.of());
        assertThat(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), UUID.fromString(held), "active"))).hasStatusOk();
        withSettings(admin, Map.of("twoFactorRoleIds", List.of(held, spare)), () -> {
            assertRefused(send(HttpMethod.DELETE, "/api/roles/" + held, admin, null), HttpStatus.CONFLICT, "role_in_use");
            assertThat(send(HttpMethod.DELETE, "/api/roles/" + spare, admin, null)).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(strings(body(get("/api/settings", admin)).get("twoFactorRoleIds"))).containsExactly(held);
        });
    }

    /** A new role granting these permissions; its id */
    protected String newRole(Browser admin, List<String> permissions) {
        var role = send(HttpMethod.POST, "/api/roles", admin, Map.of("name", "Rule " + UUID.randomUUID(), "permissions", permissions));
        assertThat(role).hasStatusOk();
        return body(role).get("id").asString();
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

    protected static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
