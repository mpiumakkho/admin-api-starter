package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.support.ApiIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** "Rules the server must check" in API.md, one by one, over HTTP */
class AccessRulesIT extends ApiIntegrationTest {

    @Test
    void rule_1_each_request_needs_its_permission() {
        var viewer = newUser("Viewer", "viewer-pass-1");
        var admin = admin();
        assertRefused(send(HttpMethod.POST, "/api/users", viewer, draft(uniqueEmail(), roleId(admin, "Viewer"), "active")),
                HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.PATCH, "/api/settings", viewer, Map.of("inviteExpiryDays", 3)), HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.POST, "/api/roles", viewer, Map.of("name", "Mine")), HttpStatus.FORBIDDEN, "forbidden");
        // Reading needs only a sign-in, and the activity log is empty without activity.view
        assertThat(get("/api/roles", viewer)).hasStatusOk();
        assertThat(get("/api/settings", viewer)).hasStatusOk();
        assertThat(get("/api/activity", viewer)).hasStatusOk().bodyJson().isEqualTo("[]");
    }

    @Test
    void without_users_view_people_see_only_themselves() {
        var admin = admin();
        var role = body(send(HttpMethod.POST, "/api/roles", admin, Map.of("name", "Nothing " + UUID.randomUUID(), "permissions", List.of())));
        var nobody = newUser(role.get("name").asString(), "nobody-pass-1");
        var users = body(get("/api/users", nobody));
        assertThat(users).hasSize(1);
        assertThat(users.get(0).get("id").asString()).isEqualTo(nobody.userId().toString());
    }

    @Test
    void without_users_edit_people_change_only_their_own_name_phone_and_photo() {
        var viewer = newUser("Viewer", "viewer-pass-1");
        assertThat(send(HttpMethod.PATCH, "/api/users/" + viewer.userId(), viewer, Map.of("phone", "0812345678")))
                .hasStatusOk().bodyJson().extractingPath("$.phone").isEqualTo("0812345678");
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + viewer.userId(), viewer, Map.of("email", uniqueEmail())),
                HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + admin().userId(), viewer, Map.of("phone", "1")),
                HttpStatus.FORBIDDEN, "forbidden");
    }

    @Test
    void rule_2_nobody_gives_out_more_than_they_have() {
        var manager = newUser("Manager", "manager-pass-1");
        var admin = admin();
        var administrator = roleId(admin, "Administrator");
        assertRefused(send(HttpMethod.POST, "/api/users", manager, draft(uniqueEmail(), administrator, "active")),
                HttpStatus.FORBIDDEN, "forbidden");
        assertThat(send(HttpMethod.POST, "/api/users", manager, draft(uniqueEmail(), roleId(admin, "Support"), "active"))).hasStatusOk();
    }

    @Test
    void rule_3_nobody_manages_someone_whose_role_allows_more() {
        var support = newUser("Support", "support-pass-1");
        var manager = newUser("Manager", "manager-pass-1");
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + manager.userId(), support, Map.of("department", "Sales")),
                HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.POST, "/api/users/" + manager.userId() + "/reset-password", support, null),
                HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(get("/api/users/" + manager.userId() + "/sessions", support), HttpStatus.FORBIDDEN, "forbidden");
    }

    @Test
    void rule_4_nobody_suspends_deletes_or_demotes_themselves() {
        var admin = admin();
        var self = List.of(admin.userId().toString());
        assertRefused(send(HttpMethod.POST, "/api/users/status", admin, Map.of("ids", self, "status", "suspended")),
                HttpStatus.FORBIDDEN, "self_change");
        assertRefused(send(HttpMethod.POST, "/api/users/delete", admin, Map.of("ids", self)), HttpStatus.FORBIDDEN, "self_change");
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + admin.userId(), admin, Map.of("roleId", roleId(admin, "Viewer").toString())),
                HttpStatus.FORBIDDEN, "self_change");
        assertRefused(send(HttpMethod.PATCH, "/api/roles/" + roleId(admin, "Administrator"), admin, Map.of("permissions", List.of("users.view"))),
                HttpStatus.FORBIDDEN, "self_change");
    }

    @Test
    void rule_5_only_the_fields_a_request_may_set_of_the_right_kind() {
        var admin = admin();
        var viewer = roleId(admin, "Viewer");
        var withId = new java.util.HashMap<String, Object>(draft(uniqueEmail(), viewer, "active"));
        withId.put("id", UUID.randomUUID().toString());
        assertRefused(send(HttpMethod.POST, "/api/users", admin, withId), HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + admin.userId(), admin, "{\"firstName\": 5}"), HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("inviteExpiryDays", 1.5)), HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.POST, "/api/roles", admin, Map.of("name", "Bad", "permissions", List.of("users.fly"))),
                HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.PATCH, "/api/users/not-a-uuid", admin, Map.of()), HttpStatus.NOT_FOUND, "not_found");
    }

    @Test
    void several_people_get_the_same_role_at_once_and_each_change_is_logged() {
        var admin = admin();
        var viewer = roleId(admin, "Viewer");
        var support = roleId(admin, "Support");
        var first = body(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), viewer, "active"))).get("id").asString();
        var second = body(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), support, "active"))).get("id").asString();

        // Only the people whose role changed come back
        var changed = body(send(HttpMethod.POST, "/api/users/role", admin, Map.of("ids", List.of(first, second, first), "roleId", support)));
        assertThat(changed).hasSize(1);
        assertThat(changed.get(0).get("id").asString()).isEqualTo(first);
        assertThat(changed.get(0).get("roleId").asString()).isEqualTo(support.toString());

        var entries = body(get("/api/activity", admin)).valueStream()
                .filter(entry -> entry.get("action").asString().equals("user.updated") && entry.has("targetId"))
                .toList();
        var logged = entries.stream().filter(entry -> entry.get("targetId").asString().equals(first)).toList();
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).get("actorId").asString()).isEqualTo(admin.userId().toString());
        var change = logged.get(0).get("changes").get(0);
        assertThat(change.get("field").asString()).isEqualTo("roleId");
        assertThat(change.get("from").asString()).isEqualTo(viewer.toString());
        assertThat(change.get("to").asString()).isEqualTo(support.toString());
        assertThat(entries).noneMatch(entry -> entry.get("targetId").asString().equals(second));
    }

    @Test
    void a_role_change_for_several_people_follows_the_rules() {
        var admin = admin();
        var manager = newUser("Manager", "manager-pass-1");
        var viewer = roleId(admin, "Viewer");
        var target = body(send(HttpMethod.POST, "/api/users", admin, draft(uniqueEmail(), viewer, "active"))).get("id").asString();
        var support = roleId(admin, "Support").toString();

        assertRefused(send(HttpMethod.POST, "/api/users/role", manager,
                Map.of("ids", List.of(target, manager.userId().toString()), "roleId", support)), HttpStatus.FORBIDDEN, "self_change");
        assertRefused(send(HttpMethod.POST, "/api/users/role", manager,
                Map.of("ids", List.of(target), "roleId", roleId(admin, "Administrator").toString())), HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.POST, "/api/users/role", manager,
                Map.of("ids", List.of(target, admin.userId().toString()), "roleId", support)), HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.POST, "/api/users/role", manager,
                Map.of("ids", List.of(target), "roleId", UUID.randomUUID().toString())), HttpStatus.NOT_FOUND, "not_found");
        assertRefused(send(HttpMethod.POST, "/api/users/role", newUser("Viewer", "viewer-pass-1"),
                Map.of("ids", List.of(target), "roleId", support)), HttpStatus.FORBIDDEN, "forbidden");

        // None of that changed anything; a role the manager could give out does
        assertThat(roleOf(admin, target)).isEqualTo(viewer.toString());
        assertThat(send(HttpMethod.POST, "/api/users/role", manager, Map.of("ids", List.of(target), "roleId", support))).hasStatusOk();
        assertThat(roleOf(admin, target)).isEqualTo(support);
    }

    @Test
    void an_import_adds_every_row_or_none() {
        var admin = admin();
        var viewer = roleId(admin, "Viewer");
        var first = uniqueEmail();
        var rows = List.of(draft(first, viewer, "active"), draft(first.toUpperCase(), viewer, "active"));
        assertRefused(send(HttpMethod.POST, "/api/users/import", admin, Map.of("users", rows)), HttpStatus.CONFLICT, "email_taken");
        assertThat(emails(body(get("/api/users", admin)))).doesNotContain(first);

        var second = uniqueEmail();
        assertThat(send(HttpMethod.POST, "/api/users/import", admin,
                Map.of("users", List.of(draft(first, viewer, "active"), draft(second, viewer, "invited"))))).hasStatusOk();
        assertThat(emails(body(get("/api/users", admin)))).contains(first, second);
        assertThat(invitationToken(second)).isNotBlank();
    }

    @Test
    void emails_are_unique_ignoring_case_and_follow_the_allowed_domains() {
        var admin = admin();
        var viewer = roleId(admin, "Viewer");
        var email = uniqueEmail();
        assertThat(send(HttpMethod.POST, "/api/users", admin, draft(email, viewer, "active"))).hasStatusOk();
        assertRefused(send(HttpMethod.POST, "/api/users", admin, draft(email.toUpperCase(), viewer, "active")),
                HttpStatus.CONFLICT, "email_taken");

        assertThat(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("allowedDomains", List.of(" Example.COM ", "example.com", ""))))
                .hasStatusOk().bodyJson().extractingPath("$.allowedDomains").isEqualTo(List.of("example.com"));
        try {
            assertRefused(send(HttpMethod.POST, "/api/users", admin, draft("someone@elsewhere.org", viewer, "active")),
                    HttpStatus.UNPROCESSABLE_CONTENT, "email_domain");
        } finally {
            send(HttpMethod.PATCH, "/api/settings", admin, Map.of("allowedDomains", List.of()));
        }
    }

    @Test
    void roles_that_are_built_in_held_or_the_default_cannot_be_deleted() {
        var admin = admin();
        assertRefused(send(HttpMethod.DELETE, "/api/roles/" + roleId(admin, "Manager"), admin, null), HttpStatus.CONFLICT, "system_role");
        assertRefused(send(HttpMethod.DELETE, "/api/roles/" + roleId(admin, "Viewer"), admin, null), HttpStatus.CONFLICT, "role_in_use");

        var role = body(send(HttpMethod.POST, "/api/roles", admin, Map.of("name", "Spare " + UUID.randomUUID())));
        var previous = body(get("/api/settings", admin)).get("defaultRoleId").asString();
        send(HttpMethod.PATCH, "/api/settings", admin, Map.of("defaultRoleId", role.get("id").asString()));
        try {
            assertRefused(send(HttpMethod.DELETE, "/api/roles/" + role.get("id").asString(), admin, null), HttpStatus.CONFLICT, "default_role");
        } finally {
            send(HttpMethod.PATCH, "/api/settings", admin, Map.of("defaultRoleId", previous));
        }
        assertThat(send(HttpMethod.DELETE, "/api/roles/" + role.get("id").asString(), admin, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void every_change_is_in_the_activity_log_with_what_changed() {
        var admin = admin();
        var email = uniqueEmail();
        var user = body(send(HttpMethod.POST, "/api/users", admin, draft(email, roleId(admin, "Viewer"), "active")));
        send(HttpMethod.PATCH, "/api/users/" + user.get("id").asString(), admin, Map.of("department", "Sales"));

        var updated = body(get("/api/activity", admin)).valueStream()
                .filter(entry -> entry.get("action").asString().equals("user.updated")
                        && entry.get("targetId").asString().equals(user.get("id").asString()))
                .findFirst().orElseThrow();
        assertThat(updated.get("actorId").asString()).isEqualTo(admin.userId().toString());
        assertThat(updated.get("changes").get(0).get("field").asString()).isEqualTo("department");
        assertThat(updated.get("changes").get(0).get("from").asString()).isEqualTo("Design");
        assertThat(updated.get("changes").get(0).get("to").asString()).isEqualTo("Sales");
    }

    private String roleOf(Browser as, String userId) {
        return body(get("/api/users", as)).valueStream()
                .filter(user -> user.get("id").asString().equals(userId))
                .map(user -> user.get("roleId").asString())
                .findFirst().orElseThrow();
    }

    private static List<String> emails(JsonNode users) {
        return users.valueStream().map(user -> user.get("email").asString()).toList();
    }
}
