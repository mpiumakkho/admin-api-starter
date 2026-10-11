package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.identity.service.RoleAssignmentExpiryJob;
import com.adminstarter.support.ApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** "Role assignments" in API.md (P-019): several roles, for a time, by each role's rules, and someone who can run it all */
class RoleAssignmentsIT extends ApiIntegrationTest {

    @Autowired
    RoleAssignmentExpiryJob expiryJob;

    @Test
    void a_second_role_for_a_time_counts_until_it_ends() {
        var admin = admin();
        var viewer = newUser("Viewer", "viewer-pass-1");
        var support = roleId(admin, "Support");
        // Without Support, the viewer cannot see the devices of someone else
        var other = newUser("Viewer", "viewer-pass-2");
        assertRefused(get("/api/users/" + other.userId() + "/sessions", viewer), HttpStatus.FORBIDDEN, "forbidden");

        var user = body(send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), support,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(1)).toString(), "reason", "Covering"))));
        var given = assignment(user, support);
        assertThat(given.get("reason").asString()).isEqualTo("Covering");
        assertThat(given.get("assignedById").asString()).isEqualTo(admin.userId().toString());
        // The next request sees it: Support has users.edit
        assertThat(get("/api/users/" + other.userId() + "/sessions", viewer)).hasStatusOk();
        assertThat(rolesOf(admin, viewer.userId().toString())).containsExactlyInAnyOrder(roleId(admin, "Viewer").toString(), support.toString());
    }

    @Test
    void an_ended_assignment_stops_counting_and_the_server_logs_it_once() throws InterruptedException {
        var admin = admin();
        var viewer = newUser("Viewer", "viewer-pass-1");
        var support = roleId(admin, "Support");
        var other = newUser("Viewer", "viewer-pass-2");
        send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), support,
                Map.of("endsAt", Instant.now().plusMillis(1500).toString())));
        assertThat(get("/api/users/" + other.userId() + "/sessions", viewer)).hasStatusOk();

        Thread.sleep(2000);
        // At its end, though the account is cached
        assertRefused(get("/api/users/" + other.userId() + "/sessions", viewer), HttpStatus.FORBIDDEN, "forbidden");
        expiryJob.run();
        expiryJob.run();
        var ended = body(get("/api/activity", admin)).valueStream()
                .filter(entry -> entry.get("action").asString().equals("role_assignment.expired")
                        && entry.get("targetId").asString().equals(viewer.userId().toString()))
                .toList();
        assertThat(ended).hasSize(1);
        assertThat(ended.get(0).get("actorId").isNull()).isTrue();
        assertThat(ended.get(0).get("changes").get(0).get("from").asString()).isEqualTo(support.toString());
        // It stays on the user, ended, to be renewed
        var user = user(admin, viewer.userId());
        var assignment = assignment(user, support);
        var renewed = send(HttpMethod.PATCH, "/api/role-assignments/" + assignment.get("id").asString(), admin,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(1)).toString()));
        assertThat(renewed).hasStatusOk();
        assertThat(get("/api/users/" + other.userId() + "/sessions", viewer)).hasStatusOk();
    }

    @Test
    void each_role_has_its_own_rules_and_is_given_once_at_a_time() {
        var admin = admin();
        var role = body(send(HttpMethod.POST, "/api/roles", admin, Map.of("name", "Contractor " + UUID.randomUUID(),
                "permissions", List.of("users.view"), "assignment", Map.of("maxDays", 30, "reasonRequired", true))));
        assertThat(role.get("assignment").get("maxDays").asInt()).isEqualTo(30);
        var roleId = UUID.fromString(role.get("id").asString());
        var someone = body(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), roleId(admin, "Viewer"), "active")));
        var userId = UUID.fromString(someone.get("id").asString());
        var inTenDays = Instant.now().plus(Duration.ofDays(10)).toString();

        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(userId, roleId, Map.of("reason", "Project"))),
                HttpStatus.UNPROCESSABLE_CONTENT, "assignment_too_long");
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(userId, roleId,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(31)).toString(), "reason", "Project"))),
                HttpStatus.UNPROCESSABLE_CONTENT, "assignment_too_long");
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(userId, roleId, Map.of("endsAt", inTenDays))),
                HttpStatus.UNPROCESSABLE_CONTENT, "reason_required");
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(userId, roleId,
                Map.of("endsAt", Instant.now().minusSeconds(60).toString(), "reason", "Project"))), HttpStatus.BAD_REQUEST, "invalid_request");
        var user = body(send(HttpMethod.POST, "/api/role-assignments", admin, grant(userId, roleId,
                Map.of("endsAt", inTenDays, "reason", "Project"))));
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(userId, roleId,
                Map.of("endsAt", inTenDays, "reason", "Again"))), HttpStatus.CONFLICT, "already_assigned");

        // Moved within its rules; left-out fields stay, and a role that must end cannot be made for good
        var id = assignment(user, roleId).get("id").asString();
        assertRefused(send(HttpMethod.PATCH, "/api/role-assignments/" + id, admin,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(40)).toString())), HttpStatus.UNPROCESSABLE_CONTENT, "assignment_too_long");
        var forGood = new HashMap<String, Object>();
        forGood.put("endsAt", null);
        assertRefused(send(HttpMethod.PATCH, "/api/role-assignments/" + id, admin, forGood), HttpStatus.UNPROCESSABLE_CONTENT, "assignment_too_long");
        var changed = body(send(HttpMethod.PATCH, "/api/role-assignments/" + id, admin, Map.of("reason", "Website project")));
        assertThat(assignment(changed, roleId).get("reason").asString()).isEqualTo("Website project");
        assertThat(assignment(changed, roleId).get("endsAt").isNull()).isFalse();
        var removed = body(send(HttpMethod.DELETE, "/api/role-assignments/" + id, admin, null));
        assertThat(removed.get("roles").valueStream().map(item -> item.get("roleId").asString())).doesNotContain(roleId.toString());
    }

    @Test
    void a_role_with_no_limit_can_be_made_for_good_again() {
        var admin = admin();
        var someone = body(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), roleId(admin, "Viewer"), "active")));
        var userId = UUID.fromString(someone.get("id").asString());
        var support = roleId(admin, "Support");
        var user = body(send(HttpMethod.POST, "/api/role-assignments", admin, grant(userId, support,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(3)).toString()))));
        var forGood = new HashMap<String, Object>();
        forGood.put("endsAt", null);
        var changed = body(send(HttpMethod.PATCH, "/api/role-assignments/" + assignment(user, support).get("id").asString(), admin, forGood));
        assertThat(assignment(changed, support).get("endsAt").isNull()).isTrue();
    }

    @Test
    void a_role_someone_will_hold_later_cannot_be_deleted() {
        var admin = admin();
        var role = body(send(HttpMethod.POST, "/api/roles", admin, Map.of("name", "Later " + UUID.randomUUID(), "permissions", List.of("users.view"))));
        var roleId = UUID.fromString(role.get("id").asString());
        var someone = body(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), roleId(admin, "Viewer"), "active")));
        send(HttpMethod.POST, "/api/role-assignments", admin, grant(UUID.fromString(someone.get("id").asString()), roleId,
                Map.of("startsAt", Instant.now().plus(Duration.ofDays(1)).toString(), "endsAt", Instant.now().plus(Duration.ofDays(2)).toString())));
        assertRefused(send(HttpMethod.DELETE, "/api/roles/" + roleId, admin, null), HttpStatus.CONFLICT, "role_in_use");
    }

    @Test
    void built_in_roles_keep_their_name_and_permissions() {
        var admin = admin();
        var manager = roleId(admin, "Manager");
        assertRefused(send(HttpMethod.PATCH, "/api/roles/" + manager, admin, Map.of("permissions", List.of("users.view"))),
                HttpStatus.CONFLICT, "system_role");
        assertRefused(send(HttpMethod.PATCH, "/api/roles/" + manager, admin, Map.of("name", "Boss")), HttpStatus.CONFLICT, "system_role");
        var changed = body(send(HttpMethod.PATCH, "/api/roles/" + manager, admin,
                Map.of("tone", "warning", "assignment", Map.of("maxDays", 90, "reasonRequired", false))));
        assertThat(changed.get("tone").asString()).isEqualTo("warning");
        assertThat(changed.get("assignment").get("maxDays").asInt()).isEqualTo(90);
        send(HttpMethod.PATCH, "/api/roles/" + manager, admin, Map.of("tone", "info", "assignment", Map.of("reasonRequired", false)));
    }

    @Test
    void someone_must_keep_a_role_with_every_permission_for_good() {
        var admin = admin();
        var administrator = roleId(admin, "Administrator");
        // Everyone else's administrator role goes; the first administrator keeps theirs
        var others = body(get("/api/users", admin)).valueStream()
                .filter(user -> !user.get("id").asString().equals(admin.userId().toString()))
                .filter(user -> user.get("roles").valueStream().anyMatch(item -> item.get("roleId").asString().equals(administrator.toString())))
                .map(user -> user.get("id").asString())
                .toList();
        if (!others.isEmpty()) {
            assertThat(send(HttpMethod.POST, "/api/role-assignments/batch", admin,
                    Map.of("userIds", others, "removeRoleId", administrator.toString()))).hasStatusOk();
        }
        // Someone gets it for a day only, and tries to take it from the last one who has it for good
        var helper = newUser("Viewer", "helper-pass-1");
        send(HttpMethod.POST, "/api/role-assignments", admin, grant(helper.userId(), administrator,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(1)).toString())));
        var helperNow = signIn(helper.user().get("email").asString(), "helper-pass-1");
        var kept = assignment(user(helperNow, admin.userId()), administrator).get("id").asString();

        assertRefused(send(HttpMethod.DELETE, "/api/role-assignments/" + kept, helperNow, null), HttpStatus.CONFLICT, "last_administrator");
        assertRefused(send(HttpMethod.PATCH, "/api/role-assignments/" + kept, helperNow,
                Map.of("endsAt", Instant.now().plus(Duration.ofDays(2)).toString())), HttpStatus.CONFLICT, "last_administrator");
        assertRefused(send(HttpMethod.POST, "/api/users/status", helperNow,
                Map.of("ids", List.of(admin.userId().toString()), "status", "suspended")), HttpStatus.CONFLICT, "last_administrator");
        assertRefused(send(HttpMethod.POST, "/api/users/delete", helperNow,
                Map.of("ids", List.of(admin.userId().toString()))), HttpStatus.CONFLICT, "last_administrator");
        // Nothing changed
        assertThat(rolesOf(admin, admin.userId().toString())).contains(administrator.toString());
    }

    private static Map<String, Object> grant(UUID userId, UUID roleId, Map<String, String> more) {
        var body = new HashMap<String, Object>(more);
        body.put("userId", userId.toString());
        body.put("roleId", roleId.toString());
        return body;
    }

    private JsonNode user(Browser as, UUID userId) {
        return body(get("/api/users", as)).valueStream()
                .filter(user -> user.get("id").asString().equals(userId.toString()))
                .findFirst().orElseThrow();
    }

    private static JsonNode assignment(JsonNode user, UUID roleId) {
        return user.get("roles").valueStream()
                .filter(item -> item.get("roleId").asString().equals(roleId.toString()))
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}
