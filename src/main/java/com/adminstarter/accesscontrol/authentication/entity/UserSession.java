package com.adminstarter.accesscontrol.authentication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/** One sign-in on one browser, from login until someone ends it or its refresh cookie runs out */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID userId;
    private Instant createdAt;
    private Instant lastSeenAt;
    /** Moves forward with every refresh */
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

    public void seen(Instant now) {
        this.lastSeenAt = now;
    }

    /** The person confirmed their password (P-023) */
    public void verified(Instant now) {
        this.verifiedAt = now;
        this.lastSeenAt = now;
    }

    public void extend(Instant expiresAt, Instant now) {
        this.expiresAt = expiresAt;
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
