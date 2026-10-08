package com.adminstarter.accesscontrol.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A password someone used before, kept so that they cannot choose it again too soon (the settings' passwordHistory,
 * P-017). Only the hash; a user has at most PasswordPolicy.KEPT of them, and they go when the user is deleted.
 */
@Entity
@Table(name = "previous_passwords")
public class PreviousPassword {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID userId;
    private String passwordHash;
    /** When it stopped being the user's password */
    private Instant createdAt;

    protected PreviousPassword() {}

    public PreviousPassword(UUID userId, String passwordHash, Instant now) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.createdAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
