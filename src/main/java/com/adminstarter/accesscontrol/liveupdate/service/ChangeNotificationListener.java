package com.adminstarter.accesscontrol.liveupdate.service;

import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

/**
 * Hears {@code NOTIFY admin_changes} from every server instance and passes it to ChangeStreamService (P-015). It keeps
 * one connection of the pool for itself, waiting on it in a virtual thread; when the connection is lost it connects
 * again, waiting longer each time, and then tells the streams that something may have changed in between.
 */
@Service
public class ChangeNotificationListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ChangeNotificationListener.class);
    /** How long one wait for notifications lasts, so that stopping is noticed */
    private static final int WAIT_MS = 1_000;
    private static final Duration FIRST_RETRY = Duration.ofSeconds(1);
    private static final Duration LAST_RETRY = Duration.ofSeconds(30);

    private final DataSource dataSource;
    private final ChangeStreamService changes;
    private volatile boolean running;
    private @Nullable Thread thread;

    public ChangeNotificationListener(DataSource dataSource, ChangeStreamService changes) {
        this.dataSource = dataSource;
        this.changes = changes;
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofVirtual().name("change-notifications").start(this::listen);
    }

    @Override
    public void stop() {
        running = false;
        var listening = thread;
        if (listening != null) {
            try {
                listening.join(Duration.ofMillis(WAIT_MS * 2L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listen() {
        var retry = FIRST_RETRY;
        var reconnecting = false;
        while (running) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("LISTEN " + ChangeStreamService.CHANNEL);
                var notifications = connection.unwrap(PGConnection.class);
                retry = FIRST_RETRY;
                if (reconnecting) {
                    // Changes made while the connection was down were not heard: have the dashboards look again
                    changes.changed();
                    reconnecting = false;
                }
                while (running) {
                    var received = notifications.getNotifications(WAIT_MS);
                    if (received != null && received.length > 0) {
                        changes.changed();
                    }
                }
            } catch (SQLException e) {
                if (!running) {
                    return;
                }
                log.warn("Lost the connection that hears about changes; connecting again in {}", retry, e);
                reconnecting = true;
                try {
                    Thread.sleep(retry);
                } catch (InterruptedException interrupted) {
                    return;
                }
                retry = retry.multipliedBy(2).compareTo(LAST_RETRY) > 0 ? LAST_RETRY : retry.multipliedBy(2);
            }
        }
    }
}
