package com.adminstarter.accesscontrol.authentication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/** One sign-in on one browser, from login until someone ends it, it goes idle, or its lifetime from login is over */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    /** How often "last seen" is brought up to date, so every request is not a write */
    public static final Duration SEEN_EVERY = Duration.ofMinutes(1);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID userId;
    private Instant createdAt;
    private Instant lastSeenAt;
    /** Its sign-in and its lifetime: refreshing does not move it, so it ends however often it refreshes (P-037) */
    private Instant expiresAt;
    /** When it last proved who is signed in: its sign-in, or a password confirmed since; refreshing does not count */
    private Instant verifiedAt;
    /** "Keep me signed in": the cookie outlives the browser, and the session lasts longer */
    private boolean remember;
    private String ip;
    private String browser;
    private String os;
    private @Nullable Instant endedAt;

    protected UserSession() {}

    public UserSession(UUID userId, boolean remember, String ip, String browser, String os, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.remember = remember;
        this.ip = ip;
        this.browser = browser;
        this.os = os;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.verifiedAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean openAt(Instant now) {
        return endedAt == null && now.isBefore(expiresAt);
    }

    /**
     * No request for longer than {@code timeout} (zero: never idle), give or take the minute lastSeenAt is kept to
     * (P-027)
     */
    public boolean idleAt(Instant now, Duration timeout) {
        return !timeout.isZero() && lastSeenAt.plus(timeout).plus(SEEN_EVERY).isBefore(now);
    }

    public void seen(Instant now) {
        this.lastSeenAt = now;
    }

    /** The person confirmed their password (P-023) */
    public void verified(Instant now) {
        this.verifiedAt = now;
        this.lastSeenAt = now;
    }


    public void end(Instant now) {
        if (endedAt == null) {
            this.endedAt = now;
        }
    }

    /** "Chrome · Windows", as the activity log names a device */
    public String getDeviceLabel() {
        return browser + " · " + os;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public boolean isRemember() {
        return remember;
    }

    public String getIp() {
        return ip;
    }

    public String getBrowser() {
        return browser;
    }

    public String getOs() {
        return os;
    }
}
