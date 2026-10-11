package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.support.ApiIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** "Branch administrators" in API.md (P-024): a role limited to a branch looks after the people of that branch */
class BranchAdministratorsIT extends ApiIntegrationTest {

    private Browser admin;
    private UUID here;
    private UUID there;
    /** A role for everyone that allows nothing in user management but reading the roles */
    private UUID staff;
    /** The branch manager: Staff for the whole organization, Manager limited to "here" */
    private Browser manager;
    /** Belongs here, with Staff */
    private String member;
    /** Belongs here, with Support for the whole organization: more than the manager has there */
    private String senior;
    /** Belongs there, with Staff */
    private String outsider;

    @BeforeEach
    void branchWithAManager() {
        admin = admin();
        here = branch("Here");
        there = branch("There");
        var staffName = "Staff " + UUID.randomUUID();
        staff = id(send(HttpMethod.POST, "/api/roles", admin, Map.of("name", staffName, "permissions", List.of("roles.view"))));
        manager = newUser(staffName, "manager-pass-1");
        assertThat(send(HttpMethod.POST, "/api/role-assignments", admin, Map.of("userId", manager.userId().toString(),
                "roleId", roleId(admin, "Manager").toString(), "branchId", here.toString()))).hasStatusOk();
        member = person(admin, staff, here);
        senior = person(admin, roleId(admin, "Support"), here);
        outsider = person(admin, staff, there);
    }

    @Test
    void sees_and_changes_only_the_people_of_their_branch_they_could_give_every_role_of() {
        var people = body(get("/api/users", manager)).valueStream().map(user -> user.get("id").asString()).toList();
        assertThat(people).contains(member, senior, manager.userId().toString()).doesNotContain(outsider);

        assertThat(send(HttpMethod.PATCH, "/api/users/" + member, manager, Map.of("department", "Sales"))).hasStatusOk();
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + outsider, manager, Map.of("department", "Sales")),
                HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + senior, manager, Map.of("department", "Sales")),
                HttpStatus.FORBIDDEN, "forbidden");
        // They stay in the branches the manager looks after
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + member, manager, Map.of("branchId", there.toString())),
                HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.PATCH, "/api/users/" + member, manager, "{\"branchId\":null}"), HttpStatus.FORBIDDEN, "forbidden");

        assertThat(send(HttpMethod.POST, "/api/users/status", manager,
                Map.of("ids", List.of(member), "status", "suspended", "reason", "On leave"))).hasStatusOk();
        // Deleting needs users.delete for the whole organization
        assertRefused(send(HttpMethod.POST, "/api/users/delete", manager, Map.of("ids", List.of(member), "reason", "Left")),
                HttpStatus.FORBIDDEN, "forbidden");
    }

    @Test
    void adds_people_to_their_branch_and_gives_roles_limited_to_it() {
        var settings = body(get("/api/settings", admin));
        var created = body(send(HttpMethod.POST, "/api/users", manager, user(here, null)));
        // Without a role: the default role, limited to the branch
        var first = created.get("roles").get(0);
        assertThat(first.get("roleId").asString()).isEqualTo(settings.get("defaultRoleId").asString());
        assertThat(first.get("branchId").asString()).isEqualTo(here.toString());

        assertRefused(send(HttpMethod.POST, "/api/users", manager, user(there, null)), HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.POST, "/api/users", manager, user(null, null)), HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.POST, "/api/users", manager, user(here, Map.of("roleId", staff.toString()))),
                HttpStatus.FORBIDDEN, "forbidden");
        assertThat(send(HttpMethod.POST, "/api/users", manager, user(here, Map.of("roleId", staff.toString(), "branchId", here.toString()))))
                .hasStatusOk();

        var viewer = roleId(admin, "Viewer").toString();
        assertThat(send(HttpMethod.POST, "/api/role-assignments", manager,
                Map.of("userId", member, "roleId", viewer, "branchId", here.toString()))).hasStatusOk();
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", manager, Map.of("userId", member, "roleId", viewer)),
                HttpStatus.FORBIDDEN, "forbidden");
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", manager,
                Map.of("userId", member, "roleId", viewer, "branchId", there.toString())), HttpStatus.FORBIDDEN, "forbidden");
        // A role that allows more than the manager has, even in the branch
        assertRefused(send(HttpMethod.POST, "/api/role-assignments", manager,
                Map.of("userId", member, "roleId", roleId(admin, "Administrator").toString(), "branchId", here.toString())),
                HttpStatus.FORBIDDEN, "forbidden");
        // Their role for the whole organization stays out of reach
        var staffAssignment = body(get("/api/users", admin)).valueStream()
                .filter(user -> user.get("id").asString().equals(member)).findFirst().orElseThrow()
                .get("roles").valueStream().filter(item -> item.get("branchId").isNull()).findFirst().orElseThrow();
        assertRefused(send(HttpMethod.DELETE, "/api/role-assignments/" + staffAssignment.get("id").asString(), manager, null),
                HttpStatus.FORBIDDEN, "forbidden");
    }

    @Test
    void reads_the_activity_about_the_people_of_their_branch() {
        send(HttpMethod.PATCH, "/api/users/" + member, admin, Map.of("department", "Sales"));
        send(HttpMethod.PATCH, "/api/users/" + outsider, admin, Map.of("department", "Sales"));

        var people = body(get("/api/users", admin)).valueStream()
                .filter(user -> here.toString().equals(user.get("branchId").asString()))
                .map(user -> user.get("id").asString()).toList();
        var entries = body(get("/api/activity", manager)).valueStream().toList();
        assertThat(entries).isNotEmpty();
        assertThat(entries).allSatisfy(entry -> assertThat(people).contains(entry.get("targetId").asString()));
        assertThat(entries).anySatisfy(entry -> assertThat(entry.get("targetId").asString()).isEqualTo(member));
    }

    private UUID branch(String name) {
        return id(send(HttpMethod.POST, "/api/branches", admin,
                Map.of("code", "B" + UUID.randomUUID().toString().substring(0, 8), "name", name)));
    }

    /** Someone new in a branch, with one role for the whole organization */
    private String person(Browser as, UUID roleId, UUID branchId) {
        var draft = new HashMap<>(draft(uniqueEmail(), roleId, "invited"));
        draft.put("branchId", branchId.toString());
        return id(send(HttpMethod.POST, "/api/users", as, draft)).toString();
    }

    private Map<String, Object> user(@Nullable UUID branchId, @Nullable Map<String, String> role) {
        var draft = new HashMap<String, Object>(Map.of("firstName", "New", "lastName", "Person", "email", uniqueEmail(),
                "department", "Sales", "status", "invited", "roles", role == null ? List.of() : List.of(role)));
        draft.put("branchId", branchId == null ? null : branchId.toString());
        return draft;
    }

    private UUID id(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        JsonNode body = body(result);
        return UUID.fromString(body.get("id").asString());
    }
}
