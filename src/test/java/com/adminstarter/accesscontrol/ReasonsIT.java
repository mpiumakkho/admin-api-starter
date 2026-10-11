package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.support.ApiIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** "Reasons" in API.md (P-021): the changes that take or reset someone's access say why, and the log keeps it */
class ReasonsIT extends ApiIntegrationTest {

    @AfterEach
    void restoreSettings() {
        send(HttpMethod.PATCH, "/api/settings", admin(), Map.of("reasonsRequired", List.of("status", "delete")));
    }

    @Test
    void suspending_needs_a_reason_on_a_new_installation_and_the_log_keeps_it() {
        var admin = admin();
        assertThat(body(get("/api/settings", admin)).get("reasonsRequired").valueStream().map(JsonNode::asString))
                .containsExactly("status", "delete");
        var viewer = newUser("Viewer", "viewer-pass-1");
        var ids = List.of(viewer.userId().toString());

        assertRefused(send(HttpMethod.POST, "/api/users/status", admin, Map.of("ids", ids, "status", "suspended")),
                HttpStatus.UNPROCESSABLE_CONTENT, "reason_required");
        assertRefused(send(HttpMethod.POST, "/api/users/status", admin, Map.of("ids", ids, "status", "suspended", "reason", "  ")),
                HttpStatus.UNPROCESSABLE_CONTENT, "reason_required");
        assertThat(send(HttpMethod.POST, "/api/users/status", admin,
                Map.of("ids", ids, "status", "suspended", "reason", "  Left the company "))).hasStatusOk();

        var entry = latest(admin, "user.suspended");
        assertThat(entry.get("targetId").asString()).isEqualTo(viewer.userId().toString());
        assertThat(entry.get("reason").asString()).isEqualTo("Left the company");
        // An entry without a reason leaves it out
        assertThat(latest(admin, "auth.sign_in").has("reason")).isFalse();
    }

    @Test
    void a_role_given_or_taken_away_keeps_its_reason_on_the_entry() {
        var admin = admin();
        var viewer = newUser("Viewer", "viewer-pass-1");
        var support = roleId(admin, "Support");
        var user = body(send(HttpMethod.POST, "/api/role-assignments", admin, Map.of(
                "userId", viewer.userId().toString(), "roleId", support.toString(), "reason", "Covering for a colleague")));
        var added = latest(admin, "role_assignment.added");
        assertThat(added.get("reason").asString()).isEqualTo("Covering for a colleague");
        assertThat(added.get("changes").valueStream().map(change -> change.get("field").asString())).doesNotContain("reason");

        var id = user.get("roles").valueStream().filter(item -> item.get("roleId").asString().equals(support.toString()))
                .findFirst().orElseThrow().get("id").asString();
        assertThat(send(HttpMethod.DELETE, "/api/role-assignments/" + id, admin, Map.of("reason", "Project over"))).hasStatusOk();
        assertThat(latest(admin, "role_assignment.removed").get("reason").asString()).isEqualTo("Project over");
    }

    @Test
    void the_settings_decide_and_your_own_sessions_never_need_one() {
        var admin = admin();
        var settings = body(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("reasonsRequired", List.of("removeRole", "endSessions"))));
        assertThat(settings.get("reasonsRequired").valueStream().map(JsonNode::asString)).containsExactly("removeRole", "endSessions");
        var change = latest(admin, "settings.updated").get("changes").get(0);
        assertThat(change.get("field").asString()).isEqualTo("reasonsRequired");
        assertThat(change.get("removed").valueStream().map(JsonNode::asString)).containsExactly("status", "delete");
        assertRefused(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("reasonsRequired", List.of("nope"))),
                HttpStatus.BAD_REQUEST, "invalid_request");

        var viewer = newUser("Viewer", "viewer-pass-1");
        var ids = List.of(viewer.userId().toString());
        assertThat(send(HttpMethod.POST, "/api/users/status", admin, Map.of("ids", ids, "status", "suspended"))).hasStatusOk();
        var viewerRole = roleId(admin, "Viewer").toString();
        assertRefused(send(HttpMethod.POST, "/api/role-assignments/batch", admin, Map.of("userIds", ids, "removeRoleId", viewerRole)),
                HttpStatus.UNPROCESSABLE_CONTENT, "reason_required");
        // A role given carries its own reason, not the request's
        assertRefused(send(HttpMethod.POST, "/api/role-assignments/batch", admin,
                Map.of("userIds", ids, "add", Map.of("roleId", viewerRole), "reason", "x")), HttpStatus.BAD_REQUEST, "invalid_request");

        var other = newUser("Viewer", "viewer-pass-2");
        assertRefused(send(HttpMethod.POST, "/api/users/" + other.userId() + "/sessions/end", admin, null),
                HttpStatus.UNPROCESSABLE_CONTENT, "reason_required");
        assertThat(send(HttpMethod.POST, "/api/users/" + other.userId() + "/sessions/end", admin, Map.of("reason", "Lost laptop")))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(latest(admin, "auth.sessions_ended").get("reason").asString()).isEqualTo("Lost laptop");
        assertThat(send(HttpMethod.POST, "/api/users/" + admin.userId() + "/sessions/end", admin, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    /** The newest entry in the activity log with this action */
    private JsonNode latest(Browser as, String action) {
        return body(get("/api/activity", as)).valueStream()
                .filter(entry -> entry.get("action").asString().equals(action))
                .findFirst().orElseThrow();
    }
}
