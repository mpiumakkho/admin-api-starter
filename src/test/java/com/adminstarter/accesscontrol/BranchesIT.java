package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.support.ApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** "Branches" in API.md (P-020): people in one branch or none, and roles given for one branch only */
class BranchesIT extends ApiIntegrationTest {

    @Test
    void adds_renames_closes_and_deletes_a_branch_each_code_once() {
        var admin = admin();
        var code = code();
        var branch = body(send(HttpMethod.POST, "/api/branches", admin, Map.of("code", " " + code.toLowerCase() + " ", "name", " Khon Kaen ")));
        assertThat(branch.get("code").asString()).isEqualTo(code);
        assertThat(branch.get("name").asString()).isEqualTo("Khon Kaen");
        assertThat(branch.get("active").asBoolean()).isTrue();
        var id = branch.get("id").asString();

        assertRefused(send(HttpMethod.POST, "/api/branches", admin, Map.of("code", code.toLowerCase(), "name", "Again")),
                HttpStatus.CONFLICT, "code_taken");
        assertRefused(send(HttpMethod.POST, "/api/branches", admin, Map.of("code", "TOO-LONG-CODE", "name", "Long")),
                HttpStatus.BAD_REQUEST, "invalid_request");

        var closed = body(send(HttpMethod.PATCH, "/api/branches/" + id, admin, Map.of("name", "Khon Kaen East", "active", false)));
        assertThat(closed.get("active").asBoolean()).isFalse();
        var logged = latest(admin, "branch.updated");
        assertThat(logged.get("targetId").asString()).isEqualTo(id);
        assertThat(logged.get("changes").valueStream().map(change -> change.get("field").asString())).containsExactly("name", "active");

        // Everyone signed in reads them; only branches.manage changes them
        var viewer = newUser("Viewer", "viewer-pass-1");
        assertThat(body(get("/api/branches", viewer)).valueStream().map(item -> item.get("id").asString())).contains(id);
        assertRefused(send(HttpMethod.POST, "/api/branches", viewer, Map.of("code", code(), "name", "Mine")), HttpStatus.FORBIDDEN, "forbidden");

        assertThat(send(HttpMethod.DELETE, "/api/branches/" + id, admin, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(body(get("/api/branches", admin)).valueStream().map(item -> item.get("id").asString())).doesNotContain(id);
        assertThat(latest(admin, "branch.deleted").get("changes").get(0).get("from").asString()).isEqualTo(code);
    }

    @Test
    void people_join_an_open_branch_and_keep_it_in_use() {
        var admin = admin();
        var branch = branch(admin);
        var viewerRole = roleId(admin, "Viewer");
        var draft = new HashMap<>(draft(uniqueEmail(), viewerRole, "invited"));
        draft.put("branchId", branch.toString());
        var user = body(send(HttpMethod.POST, "/api/users", admin, draft));
        assertThat(user.get("branchId").asString()).isEqualTo(branch.toString());
        var userId = user.get("id").asString();

        assertRefused(send(HttpMethod.DELETE, "/api/branches/" + branch, admin, null), HttpStatus.CONFLICT, "branch_in_use");
        // Closed: they stay, but nobody new joins
        send(HttpMethod.PATCH, "/api/branches/" + branch, admin, Map.of("active", false));
        assertThat(send(HttpMethod.PATCH, "/api/users/" + userId, admin, Map.of("department", "Sales"))).hasStatusOk();
        var newcomer = new HashMap<>(draft(uniqueEmail(), viewerRole, "invited"));
        newcomer.put("branchId", branch.toString());
        assertRefused(send(HttpMethod.POST, "/api/users", admin, newcomer), HttpStatus.CONFLICT, "branch_closed");
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(UUID.fromString(userId), viewerRole, branch)),
                HttpStatus.CONFLICT, "branch_closed");
        newcomer.put("branchId", UUID.randomUUID().toString());
        assertRefused(send(HttpMethod.POST, "/api/users", admin, newcomer), HttpStatus.NOT_FOUND, "not_found");

        // Leaving it: null is no branch, and a left-out branchId leaves it as it is
        var left = body(send(HttpMethod.PATCH, "/api/users/" + userId, admin, nullBranch()));
        assertThat(left.get("branchId").isNull()).isTrue();
        var change = latest(admin, "user.updated").get("changes").get(0);
        assertThat(change.get("field").asString()).isEqualTo("branchId");
        assertThat(change.get("from").asString()).isEqualTo(branch.toString());
        assertThat(change.has("to")).isFalse();
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + userId, admin, Map.of("branchId", branch.toString())),
                HttpStatus.CONFLICT, "branch_closed");
        assertThat(send(HttpMethod.DELETE, "/api/branches/" + branch, admin, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void a_role_for_a_branch_counts_there_only_and_once_per_branch() {
        var admin = admin();
        var first = branch(admin);
        var second = branch(admin);
        var viewer = newUser("Viewer", "viewer-pass-1");
        var other = newUser("Viewer", "viewer-pass-2");
        var support = roleId(admin, "Support");

        var user = body(send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), support, first)));
        var given = user.get("roles").valueStream().filter(item -> item.get("roleId").asString().equals(support.toString())).findFirst().orElseThrow();
        assertThat(given.get("branchId").asString()).isEqualTo(first.toString());
        assertThat(latest(admin, "role_assignment.added").get("changes").valueStream()
                .anyMatch(change -> change.get("field").asString().equals("branchId"))).isTrue();
        // User management asks across the whole organization: Support for a branch does not reach other people's devices
        assertRefused(get("/api/users/" + other.userId() + "/sessions", viewer), HttpStatus.FORBIDDEN, "forbidden");

        assertRefused(send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), support, first)),
                HttpStatus.CONFLICT, "already_assigned");
        assertThat(send(HttpMethod.POST, "/api/role-assignments", admin, grant(viewer.userId(), support, second))).hasStatusOk();
        // Several people at once, in a scope: someone who holds it elsewhere still gets it here
        var everywhere = body(send(HttpMethod.POST, "/api/role-assignments/batch", admin, Map.of(
                "userIds", List.of(viewer.userId().toString(), other.userId().toString()),
                "add", Map.of("roleId", support.toString()))));
        assertThat(everywhere.size()).isEqualTo(2);
        assertThat(get("/api/users/" + other.userId() + "/sessions", viewer)).hasStatusOk();

        // A branch someone holds a role in is in use
        assertRefused(send(HttpMethod.DELETE, "/api/branches/" + second, admin, null), HttpStatus.CONFLICT, "branch_in_use");
    }

    @Test
    void only_roles_for_the_whole_organization_keep_an_administrator() {
        var admin = admin();
        var administrator = roleId(admin, "Administrator");
        var others = body(get("/api/users", admin)).valueStream()
                .filter(user -> !user.get("id").asString().equals(admin.userId().toString()))
                .filter(user -> user.get("roles").valueStream().anyMatch(item -> item.get("roleId").asString().equals(administrator.toString())))
                .map(user -> user.get("id").asString())
                .toList();
        if (!others.isEmpty()) {
            assertThat(send(HttpMethod.POST, "/api/role-assignments/batch", admin,
                    Map.of("userIds", others, "removeRoleId", administrator.toString()))).hasStatusOk();
        }
        // Someone gets it for good for one branch, and for a day across the whole organization
        var helper = newUser("Viewer", "helper-pass-1");
        send(HttpMethod.POST, "/api/role-assignments", admin, grant(helper.userId(), administrator, branch(admin)));
        var day = grant(helper.userId(), administrator, null);
        day.put("endsAt", Instant.now().plus(Duration.ofDays(1)).toString());
        send(HttpMethod.POST, "/api/role-assignments", admin, day);
        var helperNow = signIn(helper.user().get("email").asString(), "helper-pass-1");
        var kept = body(get("/api/users", helperNow)).valueStream()
                .filter(user -> user.get("id").asString().equals(admin.userId().toString()))
                .flatMap(user -> user.get("roles").valueStream())
                .filter(item -> item.get("roleId").asString().equals(administrator.toString()))
                .findFirst().orElseThrow().get("id").asString();

        assertRefused(send(HttpMethod.DELETE, "/api/role-assignments/" + kept, helperNow, null), HttpStatus.CONFLICT, "last_administrator");
    }

    @Test
    void the_settings_turn_branches_on() {
        var admin = admin();
        assertThat(body(get("/api/settings", admin)).get("branchesEnabled").asBoolean()).isFalse();
        var settings = body(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("branchesEnabled", true)));
        assertThat(settings.get("branchesEnabled").asBoolean()).isTrue();
        var change = latest(admin, "settings.updated").get("changes").get(0);
        assertThat(change.get("field").asString()).isEqualTo("branchesEnabled");
        assertThat(change.get("to").asString()).isEqualTo("true");
        send(HttpMethod.PATCH, "/api/settings", admin, Map.of("branchesEnabled", false));
    }

    /** A new open branch, with a code of its own */
    private UUID branch(Browser admin) {
        var branch = body(send(HttpMethod.POST, "/api/branches", admin, Map.of("code", code(), "name", "Branch " + code())));
        return UUID.fromString(branch.get("id").asString());
    }

    private static String code() {
        return "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static Map<String, Object> grant(UUID userId, UUID roleId, @Nullable UUID branchId) {
        var body = new HashMap<String, Object>();
        body.put("userId", userId.toString());
        body.put("roleId", roleId.toString());
        if (branchId != null) {
            body.put("branchId", branchId.toString());
        }
        return body;
    }

    private static Map<String, Object> nullBranch() {
        var body = new HashMap<String, Object>();
        body.put("branchId", null);
        return body;
    }

    /** The newest entry in the activity log with this action */
    private JsonNode latest(Browser as, String action) {
        return body(get("/api/activity", as)).valueStream()
                .filter(entry -> entry.get("action").asString().equals(action))
                .findFirst().orElseThrow();
    }
}
