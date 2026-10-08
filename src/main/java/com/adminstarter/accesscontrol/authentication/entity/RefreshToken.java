package com.adminstarter.accesscontrol.authentication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One refresh cookie value, kept as its SHA-256 hash. Each refresh replaces it with a new one; a replaced value used
 * again (after a short grace for two tabs) means it was copied, and the whole session ends.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID sessionId;
    private String tokenHash;
    private Instant issuedAt;
    private Instant expiresAt;
    private @Nullable Instant replacedAt;

    protected RefreshToken() {}

    public RefreshToken(UUID sessionId, String tokenHash, Instant now, Instant expiresAt) {
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.issuedAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean expiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void replace(Instant now) {
        this.replacedAt = now;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public @Nullable Instant getReplacedAt() {
        return replacedAt;
    }
}
