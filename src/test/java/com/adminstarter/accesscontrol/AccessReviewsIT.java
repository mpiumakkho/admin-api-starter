package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.adminstarter.accesscontrol.review.service.AccessReviewJob;
import com.adminstarter.support.ApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** "Access reviews" in API.md (P-028): who still needs their roles, decided by the people who look after them */
class AccessReviewsIT extends ApiIntegrationTest {

    @Autowired
    AccessReviewJob job;

    @Autowired
    JdbcTemplate jdbc;

    private Browser admin;
    private UUID here;
    /** A role of this test alone, that allows only reading users: what a branch manager may decide about */
    private UUID staff;
    private String staffName;
    /** Viewer for the whole organization, Manager limited to "here" */
    private Browser manager;
    /** Each with Staff: two here, one in another branch */
    private String member;
    private String another;
    private String outsider;

    @BeforeEach
    void staffInTwoBranches() {
        admin = admin();
        here = branch("Here");
        var there = branch("There");
        staffName = "Reviewed " + UUID.randomUUID();
        staff = id(send(HttpMethod.POST, "/api/roles", admin, Map.of("name", staffName, "permissions", List.of("users.view"))));
        manager = newUser("Viewer", "manager-pass-1");
        assertThat(send(HttpMethod.POST, "/api/role-assignments", admin, Map.of("userId", manager.userId().toString(),
                "roleId", roleId(admin, "Manager").toString(), "branchId", here.toString()))).hasStatusOk();
        member = person(here);
        another = person(here);
        outsider = person(there);
    }

    @Test
    void the_people_who_look_after_each_person_decide_and_nothing_changes_before_it_closes() {
        var review = start(Map.of("name", " Staff check ", "roleId", staff.toString(), "dueAt", inDays(7)));
        var id = review.get("id").asString();
        assertThat(review.get("name").asString()).isEqualTo("Staff check");
        assertThat(review.get("status").asString()).isEqualTo("open");
        assertThat(review.get("itemCount").asLong()).isEqualTo(3);
        assertThat(review.get("waitingCount").asLong()).isEqualTo(3);
        assertThat(changes(latest("access_review.started"))).contains("roleId=" + staff, "assignments=3");

        // The branch manager: the people of their branch, and an email saying so
        var listed = body(get("/api/access-reviews", manager)).valueStream()
                .filter(item -> item.get("id").asString().equals(id)).findFirst().orElseThrow();
        assertThat(listed.get("waitingCount").asLong()).isEqualTo(2);
        var theirs = items(manager, id);
        assertThat(theirs.stream().map(item -> item.get("userId").asString())).containsExactlyInAnyOrder(member, another);
        assertThat(theirs).allSatisfy(item -> assertThat(item.get("canDecide").asBoolean()).isTrue());
        verify(notifications).sendNotice(eq(manager.user().get("email").asString()), any(), eq("Access review: Staff check"),
                argThat(lines -> lines.get(0).contains("2 role assignments")));

        var all = items(admin, id);
        assertRefused(decide(manager, id, itemOf(all, outsider), "keep", null), HttpStatus.FORBIDDEN, "forbidden");
        var kept = body(decide(manager, id, itemOf(all, member), "keep", null)).get(0);
        assertThat(kept.get("decision").asString()).isEqualTo("keep");
        assertThat(kept.get("decidedById").asString()).isEqualTo(manager.userId().toString());
        var removed = body(decide(manager, id, itemOf(all, another), "remove", "  Moved away ")).get(0);
        assertThat(removed.get("note").asString()).isEqualTo("Moved away");
        var decided = latest("access_review.decided");
        assertThat(decided.get("reason").asString()).isEqualTo("Moved away");
        assertThat(changes(decided)).containsExactly("decision=remove", "assignments=1");
        assertThat(rolesOf(admin, another)).contains(staff.toString());

        // Someone who takes no part in it sees nothing of it, and cannot start one
        var viewer = newUser("Viewer", "viewer-pass-1");
        assertThat(body(get("/api/access-reviews", viewer)).valueStream().map(item -> item.get("id").asString())).doesNotContain(id);
        assertRefused(get("/api/access-reviews/" + id + "/items", viewer), HttpStatus.NOT_FOUND, "not_found");
        assertRefused(send(HttpMethod.POST, "/api/access-reviews", viewer, Map.of("name", "Mine", "dueAt", inDays(7))),
                HttpStatus.FORBIDDEN, "forbidden");
    }

    @Test
    void closing_takes_away_the_roles_decided_remove_and_the_review_is_then_the_record() {
        var id = start(Map.of("name", "Staff check", "roleId", staff.toString(), "dueAt", inDays(7))).get("id").asString();
        var all = items(admin, id);
        assertThat(decide(manager, id, itemOf(all, member), "keep", null)).hasStatusOk();
        assertThat(decide(admin, id, itemOf(all, outsider), "remove", "Left the company")).hasStatusOk();
        assertRefused(send(HttpMethod.POST, "/api/access-reviews/" + id + "/close", manager, null), HttpStatus.FORBIDDEN, "forbidden");

        var closed = body(send(HttpMethod.POST, "/api/access-reviews/" + id + "/close", admin, null));
        assertThat(closed.get("status").asString()).isEqualTo("closed");
        assertThat(closed.get("closedById").asString()).isEqualTo(admin.userId().toString());
        assertThat(outcomes(id)).isEqualTo(Map.of(member, "kept", outsider, "removed", another, "not_reviewed"));
        assertThat(rolesOf(admin, outsider)).doesNotContain(staff.toString());
        assertThat(rolesOf(admin, member)).contains(staff.toString());

        var removal = latest("role_assignment.removed");
        assertThat(removal.get("actorId").isNull()).isTrue();
        assertThat(removal.get("targetId").asString()).isEqualTo(outsider);
        assertThat(removal.get("reason").asString()).isEqualTo("Left the company");
        assertThat(changes(removal)).contains("accessReview=Staff check");
        var entry = latest("access_review.closed");
        assertThat(entry.get("actorId").asString()).isEqualTo(admin.userId().toString());
        assertThat(changes(entry)).containsExactly("kept=1", "removed=1", "notReviewed=1");

        // Once closed, the branch manager sees what they decided, and nothing changes any more
        assertThat(items(manager, id).stream().map(item -> item.get("userId").asString())).containsExactly(member);
        assertRefused(decide(admin, id, itemOf(all, another), "keep", null), HttpStatus.CONFLICT, "review_closed");
        assertRefused(send(HttpMethod.DELETE, "/api/access-reviews/" + id, admin, null), HttpStatus.CONFLICT, "review_closed");
    }

    @Test
    void the_server_closes_it_at_its_due_date() {
        var id = start(Map.of("name", "Due soon", "roleId", staff.toString(), "dueAt", inDays(1))).get("id").asString();
        assertThat(decide(admin, id, itemOf(items(admin, id), member), "remove", null)).hasStatusOk();
        jdbc.update("update access_control.access_reviews set due_at = now() - interval '1 minute' where id = ?", UUID.fromString(id));

        job.run();

        var review = body(get("/api/access-reviews", admin)).valueStream()
                .filter(item -> item.get("id").asString().equals(id)).findFirst().orElseThrow();
        assertThat(review.get("status").asString()).isEqualTo("closed");
        assertThat(review.get("closedById").isNull()).isTrue();
        assertThat(rolesOf(admin, member)).doesNotContain(staff.toString());
        var entry = body(get("/api/activity", admin)).valueStream()
                .filter(item -> item.get("action").asString().equals("access_review.closed") && item.get("targetId").asString().equals(id))
                .findFirst().orElseThrow();
        assertThat(entry.get("actorId").isNull()).isTrue();
    }

    @Test
    void a_named_reviewer_decides_all_but_their_own_and_the_request_must_make_sense() {
        var named = newUser(staffName, "named-pass-1");
        var id = start(Map.of("name", "Named", "roleId", staff.toString(), "dueAt", inDays(3),
                "reviewerIds", List.of(named.userId().toString()))).get("id").asString();
        var theirs = items(named, id);
        assertThat(theirs).hasSize(4);
        assertThat(theirs).allSatisfy(item -> assertThat(item.get("canDecide").asBoolean())
                .isEqualTo(!item.get("userId").asString().equals(named.userId().toString())));
        assertRefused(decide(named, id, itemOf(theirs, named.userId().toString()), "keep", null), HttpStatus.FORBIDDEN, "self_change");
        assertThat(decide(named, id, itemOf(theirs, outsider), "remove", null)).hasStatusOk();

        // The settings may ask why a role should go, as they may when taking it away
        var required = body(get("/api/settings", admin)).get("reasonsRequired").valueStream().map(JsonNode::asString).toList();
        try {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("reasonsRequired", List.of("removeRole")))).hasStatusOk();
            assertRefused(decide(named, id, itemOf(theirs, member), "remove", null), HttpStatus.UNPROCESSABLE_CONTENT, "reason_required");
            assertThat(decide(named, id, itemOf(theirs, member), "keep", null)).hasStatusOk();
        } finally {
            assertThat(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("reasonsRequired", required))).hasStatusOk();
        }

        assertRefused(send(HttpMethod.POST, "/api/access-reviews", admin, Map.of("name", "Past", "dueAt", inDays(-1))),
                HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.POST, "/api/access-reviews", admin, Map.of("name", "Too far", "dueAt", inDays(400))),
                HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.POST, "/api/access-reviews", admin, Map.of("name", "Twice", "dueAt", inDays(3),
                "reviewerIds", List.of(named.userId().toString(), named.userId().toString()))), HttpStatus.BAD_REQUEST, "invalid_request");
        assertRefused(send(HttpMethod.POST, "/api/access-reviews", admin, Map.of("name", "Ghost", "dueAt", inDays(3),
                "roleId", UUID.randomUUID().toString())), HttpStatus.NOT_FOUND, "not_found");
        assertRefused(send(HttpMethod.POST, "/api/access-reviews", admin, Map.of("name", "Nobody", "dueAt", inDays(3),
                "reviewerIds", List.of(UUID.randomUUID().toString()))), HttpStatus.NOT_FOUND, "not_found");

        // An open one can be deleted, its decisions with it
        assertThat(send(HttpMethod.DELETE, "/api/access-reviews/" + id, admin, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(latest("access_review.deleted").get("targetLabel").asString()).isEqualTo("Named");
        assertRefused(get("/api/access-reviews/" + id + "/items", admin), HttpStatus.NOT_FOUND, "not_found");
    }

    private JsonNode start(Map<String, Object> draft) {
        var result = send(HttpMethod.POST, "/api/access-reviews", admin, draft);
        assertThat(result).hasStatusOk();
        return body(result);
    }

    private List<JsonNode> items(Browser as, String reviewId) {
        var result = get("/api/access-reviews/" + reviewId + "/items", as);
        assertThat(result).hasStatusOk();
        return body(result).valueStream().toList();
    }

    /** What closing did with each person's role, by person */
    private Map<String, String> outcomes(String reviewId) {
        return items(admin, reviewId).stream()
                .collect(Collectors.toMap(item -> item.get("userId").asString(), item -> item.get("outcome").asString()));
    }

    /** The id of the person's role under review, as the one item to decide */
    private static List<String> itemOf(List<JsonNode> items, String userId) {
        return List.of(items.stream().filter(item -> item.get("userId").asString().equals(userId))
                .findFirst().orElseThrow().get("id").asString());
    }

    private MvcTestResult decide(Browser as, String reviewId, List<String> itemIds, String decision, @Nullable String note) {
        var body = new HashMap<String, Object>(Map.of("itemIds", itemIds, "decision", decision));
        if (note != null) {
            body.put("note", note);
        }
        return send(HttpMethod.POST, "/api/access-reviews/" + reviewId + "/decisions", as, body);
    }

    /** The newest entry in the activity log with this action */
    private JsonNode latest(String action) {
        return body(get("/api/activity", admin)).valueStream()
                .filter(entry -> entry.get("action").asString().equals(action))
                .findFirst().orElseThrow();
    }

    /** An entry's changes as "field=to" */
    private static List<String> changes(JsonNode entry) {
        return entry.get("changes").valueStream().map(change -> change.get("field").asString() + "="
                + (change.has("to") ? change.get("to").asString() : "")).toList();
    }

    private static String inDays(int days) {
        return Instant.now().plus(Duration.ofDays(days)).toString();
    }

    private UUID branch(String name) {
        return id(send(HttpMethod.POST, "/api/branches", admin,
                Map.of("code", "R" + UUID.randomUUID().toString().substring(0, 8), "name", name)));
    }

    /** Someone new in a branch, with Staff for the whole organization */
    private String person(UUID branchId) {
        var draft = new HashMap<>(draft(uniqueEmail(), staff, "invited"));
        draft.put("branchId", branchId.toString());
        return id(send(HttpMethod.POST, "/api/users", admin, draft)).toString();
    }

    private UUID id(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return UUID.fromString(body(result).get("id").asString());
    }
}
