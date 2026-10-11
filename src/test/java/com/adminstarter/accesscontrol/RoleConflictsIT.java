package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.support.ApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** "Conflicting roles" in API.md (P-022): roles nobody may hold at the same time */
class RoleConflictsIT extends ApiIntegrationTest {

    @Test
    void rules_are_kept_by_people_who_manage_roles_and_logged() {
        var admin = admin();
        var a = newRole(admin, "Payments ");
        var b = newRole(admin, "Payment checks ");
        var c = newRole(admin, "Refunds ");

        assertRefused(send(HttpMethod.POST, "/api/role-conflicts", admin, Map.of("name", "One", "roleIds", List.of(a.toString()))),
                HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.POST, "/api/role-conflicts", admin,
                Map.of("name", "Twice", "roleIds", List.of(a.toString(), a.toString()))), HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.POST, "/api/role-conflicts", admin,
                Map.of("name", "Ghost", "roleIds", List.of(a.toString(), UUID.randomUUID().toString()))), HttpStatus.NOT_FOUND, "not_found");

        var rule = body(send(HttpMethod.POST, "/api/role-conflicts", admin,
                Map.of("name", " Payer is not the checker ", "roleIds", List.of(b.toString(), a.toString()))));
        var id = rule.get("id").asString();
        assertThat(rule.get("name").asString()).isEqualTo("Payer is not the checker");
        // Oldest role first, as GET /roles lists them
        assertThat(rule.get("roleIds").valueStream().map(JsonNode::asString)).containsExactly(a.toString(), b.toString());
        assertThat(latest(admin, "role_conflict.created").get("targetId").asString()).isEqualTo(id);

        var changed = body(send(HttpMethod.PATCH, "/api/role-conflicts/" + id, admin, Map.of("roleIds", List.of(a.toString(), c.toString()))));
        assertThat(changed.get("roleIds").valueStream().map(JsonNode::asString)).containsExactly(a.toString(), c.toString());
        var roles = latest(admin, "role_conflict.updated").get("changes").get(0);
        assertThat(roles.get("field").asString()).isEqualTo("roles");
        assertThat(roles.get("added").valueStream().map(JsonNode::asString)).containsExactly(c.toString());
        assertThat(roles.get("removed").valueStream().map(JsonNode::asString)).containsExactly(b.toString());

        // Anyone signed in reads them; only roles.manage changes them
        var viewer = newUser("Viewer", "viewer-pass-1");
        assertThat(body(get("/api/role-conflicts", viewer)).valueStream().map(item -> item.get("id").asString())).contains(id);
        assertRefused(send(HttpMethod.DELETE, "/api/role-conflicts/" + id, viewer, null), HttpStatus.FORBIDDEN, "forbidden");
        assertThat(send(HttpMethod.DELETE, "/api/role-conflicts/" + id, admin, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(latest(admin, "role_conflict.deleted").get("targetLabel").asString()).isEqualTo("Payer is not the checker");
    }

    @Test
    void a_role_that_cannot_be_held_with_one_they_hold_is_refused_in_any_scope() {
        var admin = admin();
        var viewer = newUser("Viewer", "viewer-pass-1");
        var viewerRole = roleId(admin, "Viewer");
        var other = newRole(admin, "Billing ");
        rule(admin, viewerRole, other);
        var branch = UUID.fromString(body(send(HttpMethod.POST, "/api/branches", admin,
                Map.of("code", "C" + UUID.randomUUID().toString().substring(0, 8), "name", "Conflicts branch"))).get("id").asString());

        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), other, null)),
                HttpStatus.CONFLICT, "role_conflict");
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), other, Map.of("branchId", branch.toString()))),
                HttpStatus.CONFLICT, "role_conflict");
        var both = new HashMap<>(draft(uniqueEmail(), viewerRole, "invited"));
        both.put("roles", List.of(Map.of("roleId", viewerRole.toString()), Map.of("roleId", other.toString())));
        assertRefused(send(HttpMethod.POST, "/api/users", admin, both), HttpStatus.CONFLICT, "role_conflict");
        assertRefused(send(HttpMethod.POST, "/api/role-assignments/batch", admin,
                Map.of("userIds", List.of(viewer.userId().toString()), "add", Map.of("roleId", other.toString()))), HttpStatus.CONFLICT, "role_conflict");
    }

    @Test
    void the_times_count_one_ending_before_the_other_starts_is_fine_until_its_end_moves() {
        var admin = admin();
        var viewer = newUser("Viewer", "viewer-pass-1");
        var first = newRole(admin, "Season ");
        var second = newRole(admin, "Review ");
        rule(admin, first, second);
        send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), first,
                Map.of("startsAt", Instant.now().plus(Duration.ofDays(2)).toString())));

        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), second, null)),
                HttpStatus.CONFLICT, "role_conflict");
        var user = body(send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), second,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(1)).toString()))));
        var id = user.get("roles").valueStream().filter(item -> item.get("roleId").asString().equals(second.toString()))
                .findFirst().orElseThrow().get("id").asString();
        assertRefused(send(HttpMethod.PATCH, "/api/role-assignments/" + id, admin,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(3)).toString())), HttpStatus.CONFLICT, "role_conflict");
    }

    @Test
    void deleting_a_role_drops_a_rule_left_with_one_role() {
        var admin = admin();
        var gone = newRole(admin, "Short-lived ");
        var kept = newRole(admin, "Kept ");
        var id = rule(admin, gone, kept);

        assertThat(send(HttpMethod.DELETE, "/api/roles/" + gone, admin, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(body(get("/api/role-conflicts", admin)).valueStream().map(item -> item.get("id").asString())).doesNotContain(id);
    }

    private UUID newRole(Browser admin, String name) {
        var role = body(send(HttpMethod.POST, "/api/roles", admin, Map.of("name", name + UUID.randomUUID(), "permissions", List.of("users.view"))));
        return UUID.fromString(role.get("id").asString());
    }

    private String rule(Browser admin, UUID first, UUID second) {
        return body(send(HttpMethod.POST, "/api/role-conflicts", admin,
                Map.of("name", "Rule " + UUID.randomUUID(), "roleIds", List.of(first.toString(), second.toString())))).get("id").asString();
    }

    private static Map<String, Object> grant(UUID userId, UUID roleId, Map<String, String> more) {
        var body = new HashMap<String, Object>(more == null ? Map.of() : more);
        body.put("userId", userId.toString());
        body.put("roleId", roleId.toString());
        return body;
    }

    /** The newest entry in the activity log with this action */
    private JsonNode latest(Browser as, String action) {
        return body(get("/api/activity", as)).valueStream()
                .filter(entry -> entry.get("action").asString().equals(action))
                .findFirst().orElseThrow();
    }
}
