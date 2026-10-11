package com.adminstarter.accesscontrol.branch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A site or office people belong to, and that a role can be limited to (P-020). A closed one keeps its people and
 * their roles, but nobody new joins it or gets a role there.
 */
@Entity
@Table(name = "branches")
public class Branch {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Short and upper-case, unique ignoring case */
    private String code;
    private String name;
    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;

    protected Branch() {}

    public Branch(String code, String name, Instant now) {
        this.code = code;
        this.name = name;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String code, String name, boolean active, Instant now) {
        this.code = code;
        this.name = name;
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
