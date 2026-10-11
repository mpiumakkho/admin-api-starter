package com.adminstarter.accesscontrol.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/** One recovery code for two-step sign-in (P-025): only its hash, and when it was used, once */
@Entity
@Table(name = "recovery_codes")
public class RecoveryCode {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID userId;
    private String codeHash;
    private Instant createdAt;
    private @Nullable Instant usedAt;

    protected RecoveryCode() {}

    public RecoveryCode(UUID userId, String codeHash, Instant now) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = now;
    }

    public void use(Instant now) {
        this.usedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getUsedAt() {
        return usedAt;
    }
}
