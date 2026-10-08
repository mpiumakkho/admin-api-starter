package com.adminstarter.accesscontrol.authentication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/** The secret in an email link, kept as its SHA-256 hash; it works once, until it expires */
@Entity
@Table(name = "action_tokens")
public class ActionToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private ActionTokenType type;

    private String tokenHash;
    private Instant expiresAt;
    private @Nullable Instant usedAt;
    private Instant createdAt;

    protected ActionToken() {}

    public ActionToken(UUID userId, ActionTokenType type, String tokenHash, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.type = type;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean usableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /** Used, or replaced by a newer link */
    public void use(Instant now) {
        if (usedAt == null) {
            this.usedAt = now;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public ActionTokenType getType() {
        return type;
    }
}
