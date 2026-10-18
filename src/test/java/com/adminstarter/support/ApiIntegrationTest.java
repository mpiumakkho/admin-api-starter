package com.adminstarter.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.adminstarter.accesscontrol.notification.service.NotificationService;
import com.adminstarter.accesscontrol.notification.service.NotificationService.Recipient;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole application over HTTP (MockMvc, with the security filters) against a real PostgreSQL in a container.
 * Skipped when Docker is not running. Emails are not sent: the links they would carry are read from the mocked
 * NotificationService. Every test makes its own users, since the database is shared by the test classes.
 */
@SpringBootTest(properties = {
        "access-control.bootstrap.admin-password=" + ApiIntegrationTest.ADMIN_PASSWORD,
        // The tests sign the administrator in with a password alone; SettingsInitializerTest covers the default (P-042)
        "access-control.bootstrap.two-factor-roles=",
        "access-control.rate-limit.login=10000",
        "access-control.rate-limit.refresh=10000",
        "access-control.rate-limit.email-links=10000",
        "access-control.token.reuse-grace=0s"})
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class ApiIntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@example.com";
    protected static final String ADMIN_PASSWORD = "admin-pass-123";
    protected static final String COOKIE = "ac_rt";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JsonMapper json;

    @MockitoBean
    protected NotificationService notifications;

    /** A signed-in browser: its access token and refresh cookie */
    protected record Browser(String token, Cookie cookie, JsonNode user) {

        public UUID userId() {
            return UUID.fromString(user.get("id").asString());
        }
    }

    protected MvcTestResult login(String email, String password, @Nullable String code) {
        var body = new LinkedHashMap<String, Object>(Map.of("email", email, "password", password, "remember", false));
        if (code != null) {
            body.put("code", code);
        }
        return mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)).exchange();
    }

    protected Browser signIn(String email, String password) {
        return signedIn(login(email, password, null));
    }

    protected Browser admin() {
        return signIn(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected Browser signedIn(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        var body = body(result);
        return new Browser(body.get("accessToken").asString(), result.getResponse().getCookie(COOKIE), body.get("user"));
    }

    protected MvcTestResult get(String path, Browser as) {
        return mvc.get().uri(path).header("Authorization", "Bearer " + as.token()).exchange();
    }

    protected MvcTestResult send(HttpMethod method, String path, Browser as, @Nullable Object body) {
        var request = mvc.method(method).uri(path).header("Authorization", "Bearer " + as.token());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : json.writeValueAsString(body));
        }
        return request.exchange();
    }

    protected MvcTestResult refresh(Cookie cookie) {
        return mvc.post().uri("/api/auth/refresh").cookie(cookie).exchange();
    }

    protected JsonNode body(MvcTestResult result) {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    protected void assertRefused(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    protected UUID roleId(Browser as, String name) {
        var roles = body(get("/api/roles", as));
        return StreamSupport.stream(roles.spliterator(), false)
                .filter(role -> role.get("name").asString().equals(name))
                .map(role -> UUID.fromString(role.get("id").asString()))
                .findFirst().orElseThrow();
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    /** A new user with one role, for good */
    protected Map<String, Object> draft(String email, UUID roleId, String status) {
        return Map.of("firstName", "Test", "lastName", "User", "email", email, "department", "Design",
                "roles", List.of(Map.of("roleId", roleId.toString())), "status", status);
    }

    /** The roles a user holds now, by id, from GET /api/users */
    protected List<String> rolesOf(Browser as, String userId) {
        var now = Instant.now();
        return body(get("/api/users", as)).valueStream()
                .filter(user -> user.get("id").asString().equals(userId))
                .flatMap(user -> user.get("roles").valueStream())
                .filter(item -> !Instant.parse(item.get("startsAt").asString()).isAfter(now)
                        && (item.get("endsAt").isNull() || Instant.parse(item.get("endsAt").asString()).isAfter(now)))
                .map(item -> item.get("roleId").asString())
                .toList();
    }

    /** A new active user who set their password through the invitation email, signed in */
    protected Browser newUser(String roleName, String password) {
        var admin = admin();
        var email = uniqueEmail();
        assertThat(send(HttpMethod.POST, "/api/users", admin, draft(email, roleId(admin, roleName), "invited"))).hasStatusOk();
        var token = invitationToken(email);
        assertThat(mvc.post().uri("/api/auth/activate").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", password))).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        return signIn(email, password);
    }

    /** An email to this address, in whichever language, for verifying what NotificationService was asked to send */
    protected static Recipient to(String email) {
        return argThat(recipient -> recipient != null && recipient.email().equals(email));
    }

    /** The token in the last invitation emailed to this address */
    protected String invitationToken(String email) {
        var link = ArgumentCaptor.forClass(String.class);
        verify(notifications, org.mockito.Mockito.atLeastOnce()).sendInvitation(to(email), link.capture(), any(Instant.class));
        var query = URI.create(link.getValue()).getQuery();
        return query.substring(query.indexOf("token=") + "token=".length());
    }
}
