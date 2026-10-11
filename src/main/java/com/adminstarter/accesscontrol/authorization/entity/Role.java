package com.adminstarter.accesscontrol.authorization.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A named set of permissions. People hold roles through role assignments (P-019), each by the rules here: an end within
 * {@code assignmentMaxDays} days, or a reason.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Set for roles a RoleTemplate created, so code can find them again; null for roles made on the dashboard */
    private @Nullable String code;

    private String name;
    private String description;
    private String tone;

    @Column(name = "is_system")
    private boolean system;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    private Set<String> permissions = new HashSet<>();

    /** Each assignment must end within this many days of starting; null: no limit, and it may be for good */
    private @Nullable Integer assignmentMaxDays;
    private boolean assignmentReasonRequired;

    private Instant createdAt;
    private Instant updatedAt;

    protected Role() {}

    public Role(@Nullable String code, String name, String description, String tone, boolean system,
            Set<String> permissions, Instant now) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.tone = tone;
        this.system = system;
        this.permissions.addAll(permissions);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, String tone, Set<String> permissions, Instant now) {
        this.name = name;
        this.description = description;
        this.tone = tone;
        this.permissions.retainAll(permissions);
        this.permissions.addAll(permissions);
        this.updatedAt = now;
    }

    public void changeAssignmentRules(@Nullable Integer maxDays, boolean reasonRequired, Instant now) {
        this.assignmentMaxDays = maxDays;
        this.assignmentReasonRequired = reasonRequired;
        this.updatedAt = now;
    }

    /** A built-in role takes the name, description and permissions its RoleTemplate gives it, on every start */
    public void redefine(String name, String description, Set<String> permissions, Instant now) {
        if (!this.name.equals(name) || !this.description.equals(description) || !this.permissions.equals(permissions)) {
            update(name, description, tone, permissions, now);
        }
    }

    public UUID getId() {
        return id;
    }

    public @Nullable String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getTone() {
        return tone;
    }

    public boolean isSystem() {
        return system;
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }

    public @Nullable Integer getAssignmentMaxDays() {
        return assignmentMaxDays;
    }

    public boolean isAssignmentReasonRequired() {
        return assignmentReasonRequired;
    }
}
