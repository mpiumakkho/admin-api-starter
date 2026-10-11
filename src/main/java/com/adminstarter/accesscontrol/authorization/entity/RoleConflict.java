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

/**
 * Roles nobody may hold at the same time, so that one person cannot both do and check the same work: NIST RBAC's static
 * separation of duty (P-022). Two or more roles.
 */
@Entity
@Table(name = "role_conflicts")
public class RoleConflict {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String name;
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_conflict_roles", joinColumns = @JoinColumn(name = "conflict_id"))
    @Column(name = "role_id")
    private Set<UUID> roleIds = new HashSet<>();

    private Instant createdAt;
    private Instant updatedAt;

    protected RoleConflict() {}

    public RoleConflict(String name, String description, Set<UUID> roleIds, Instant now) {
        update(name, description, roleIds, now);
        this.createdAt = now;
    }

    public final void update(String name, String description, Set<UUID> roleIds, Instant now) {
        this.name = name;
        this.description = description;
        this.roleIds.clear();
        this.roleIds.addAll(roleIds);
        this.updatedAt = now;
    }

    /** A deleted role comes off the rule; whether enough are left for it to mean anything */
    public boolean forget(UUID roleId, Instant now) {
        if (roleIds.remove(roleId)) {
            this.updatedAt = now;
        }
        return roleIds.size() > 1;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<UUID> getRoleIds() {
        return Set.copyOf(roleIds);
    }
}
