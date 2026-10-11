package com.adminstarter.accesscontrol.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * One entry of the activity log. Written once and never changed; the people it names are kept as text and plain ids
 * (no foreign keys), so it still reads after they are deleted.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private Instant occurredAt;
    /** Null for what the server did by itself, such as ending a role assignment on time */
    private @Nullable UUID actorId;
    /** Such as "user.updated"; ActivityAction in admin-ng-starter's models.ts */
    private String action;
    private String targetLabel;
    private @Nullable String targetId;

    /** The changed fields as a JSON array (FieldChange), or null */
    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private @Nullable String changes;

    private String ip;
    private String browser;
    private String os;
    private String requestId;

    protected AuditLog() {}

    public AuditLog(Instant occurredAt, @Nullable UUID actorId, String action, String targetLabel, @Nullable String targetId,
            @Nullable String changes, String ip, String browser, String os, String requestId) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.action = action;
        this.targetLabel = targetLabel;
        this.targetId = targetId;
        this.changes = changes;
        this.ip = ip;
        this.browser = browser;
        this.os = os;
        this.requestId = requestId;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public @Nullable UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public @Nullable String getTargetId() {
        return targetId;
    }

    public @Nullable String getChanges() {
        return changes;
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
