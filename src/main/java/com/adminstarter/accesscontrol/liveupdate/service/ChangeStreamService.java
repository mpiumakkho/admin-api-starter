package com.adminstarter.accesscontrol.liveupdate.service;

import com.adminstarter.accesscontrol.audit.event.ActivityRecorded;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The event streams the dashboards keep open (GET /api/events). After a change commits, every stream gets
 * {@code data: changed} and the dashboards load their data again. Changes close together, such as deleting fifty
 * users at once, are sent as one message. A comment every 25 seconds keeps proxies from closing idle streams.
 *
 * <p>Streams live in this instance's memory: with several instances, a change made on one is only heard by the
 * streams open on it. Relay the event through the database (LISTEN/NOTIFY) or a broker before scaling out.
 */
@Service
public class ChangeStreamService {

    private static final Duration COALESCE = Duration.ofMillis(250);

    private final Set<SseEmitter> streams = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean pending = new AtomicBoolean();
    private final TaskScheduler scheduler;

    public ChangeStreamService(TaskScheduler scheduler) {
        this.scheduler = scheduler;
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

    @TransactionalEventListener
    void on(ActivityRecorded event) {
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
