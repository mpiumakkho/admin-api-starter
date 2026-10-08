package com.adminstarter.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.support.ApiIntegrationTest;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Live updates across server instances, through PostgreSQL LISTEN/NOTIFY (P-015) */
class LiveUpdateIT extends ApiIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    MockMvc mockMvc;

    /** Stands in for another server instance: a connection of its own, listening on the channel */
    Connection other;

    @BeforeEach
    void listen() throws SQLException {
        other = dataSource.getConnection();
        try (var statement = other.createStatement()) {
            statement.execute("LISTEN admin_changes");
        }
    }

    @AfterEach
    void close() throws SQLException {
        other.close();
    }

    @Test
    void a_change_is_announced_when_it_commits_and_a_refused_one_never_is() throws SQLException {
        var admin = admin();
        notifications(Duration.ofMillis(300));

        assertThat(send(HttpMethod.PATCH, "/api/settings", admin, Map.of("inviteExpiryDays", 3 + (int) (Math.random() * 20))))
                .hasStatusOk();
        assertThat(notifications(Duration.ofSeconds(5))).isPositive();

        // Deleting a role people still hold writes its activity entry, then rolls back: nothing goes out
        assertRefused(send(HttpMethod.DELETE, "/api/roles/" + roleId(admin, "Viewer"), admin, null), HttpStatus.CONFLICT, "role_in_use");
        assertThat(notifications(Duration.ofSeconds(2))).isZero();
    }

    @Test
    void a_change_on_another_instance_reaches_the_open_streams() throws Exception {
        var admin = admin();
        var stream = mockMvc.perform(MockMvcRequestBuilders.get("/api/events").header("Authorization", "Bearer " + admin.token())).andReturn();
        assertThat(stream.getRequest().isAsyncStarted()).isTrue();
        var response = stream.getResponse();
        waitFor(() -> contentOf(response).contains(":connected"));

        // The other instance commits a change
        try (var statement = other.createStatement()) {
            statement.execute("NOTIFY admin_changes, '" + UUID.randomUUID() + "'");
        }
        waitFor(() -> contentOf(response).contains("data:changed"));
    }

    /** How many notifications the other instance hears within {@code wait} */
    private int notifications(Duration wait) throws SQLException {
        var pg = other.unwrap(PGConnection.class);
        var count = 0;
        var until = Instant.now().plus(wait);
        while (Instant.now().isBefore(until)) {
            var received = pg.getNotifications(100);
            count += received == null ? 0 : received.length;
        }
        return count;
    }

    private static String contentOf(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        var until = Instant.now().plusSeconds(5);
        while (!condition.getAsBoolean()) {
            assertThat(Instant.now()).as("waited 5 seconds").isBefore(until);
            Thread.sleep(50);
        }
    }
}
