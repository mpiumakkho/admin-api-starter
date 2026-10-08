package com.adminstarter.accesscontrol.liveupdate.service;

import com.adminstarter.accesscontrol.audit.event.ActivityRecorded;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The event streams the dashboards keep open (GET /api/events). After a change commits, every stream gets
 * {@code data: changed} and the dashboards load their data again. Changes close together, such as deleting fifty
 * users at once, are sent as one message. A comment every 25 seconds keeps proxies from closing idle streams.
 *
 * <p>A change reaches the streams of every server instance through PostgreSQL (P-015): the transaction that makes it
 * sends {@code NOTIFY admin_changes}, which the database delivers when that transaction commits and drops if it rolls
 * back, and ChangeNotificationListener on each instance hears it and calls {@link #changed()}.
 */
@Service
public class ChangeStreamService {

    /** The PostgreSQL channel every instance listens on */
    static final String CHANNEL = "admin_changes";
    private static final Duration COALESCE = Duration.ofMillis(250);

    private final Set<SseEmitter> streams = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean pending = new AtomicBoolean();
    private final TaskScheduler scheduler;
    private final JdbcTemplate jdbc;

    public ChangeStreamService(TaskScheduler scheduler, JdbcTemplate jdbc) {
        this.scheduler = scheduler;
        this.jdbc = jdbc;
    }

    /** A stream that closes by itself after {@code lifetime}, when the token that opened it runs out */
    public SseEmitter open(Duration lifetime) {
        var stream = new SseEmitter(Math.max(lifetime.toMillis(), 1));
        stream.onCompletion(() -> streams.remove(stream));
        stream.onTimeout(stream::complete);
        stream.onError(error -> streams.remove(stream));
        streams.add(stream);
        // Sends the headers now, so the client knows it is connected
        send(stream, SseEmitter.event().comment("connected"));
        return stream;
    }

    /**
     * Runs in the transaction of the change, on its connection: the notification goes out with the commit. PostgreSQL
     * folds the same notification sent several times in one transaction into one.
     */
    @EventListener
    void on(ActivityRecorded event) {
        jdbc.execute("NOTIFY " + CHANNEL);
    }

    /** Something changed, on this instance or another: tells every open stream, once for changes close together */
    public void changed() {
        if (pending.compareAndSet(false, true)) {
            scheduler.schedule(this::broadcast, Instant.now().plus(COALESCE));
        }
    }

    @Scheduled(fixedRate = 25, timeUnit = TimeUnit.SECONDS)
    void ping() {
        streams.forEach(stream -> send(stream, SseEmitter.event().comment("ping")));
    }

    private void broadcast() {
        pending.set(false);
        streams.forEach(stream -> send(stream, SseEmitter.event().data("changed")));
    }

    private void send(SseEmitter stream, SseEmitter.SseEventBuilder event) {
        try {
            stream.send(event);
        } catch (IOException | IllegalStateException e) {
            // The client went away; the emitter is finished either way
            streams.remove(stream);
        }
    }
}
