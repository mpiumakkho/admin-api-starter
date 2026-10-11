package com.adminstarter.accesscontrol.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
public class User {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String department;
    /** The branch they belong to, or null for none (P-020) */
    private @Nullable UUID branchId;
    /** Image URL or data URL; empty shows initials */
    private String avatar;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private Instant createdAt;
    private Instant updatedAt;
    /** Null until the first sign-in */
    private @Nullable Instant lastActiveAt;

    protected User() {}

    /** Their roles are role assignments of their own (P-019) */
    public User(String firstName, String lastName, String email, String phone, String department, String avatar,
            UserStatus status, Instant now) {
        update(firstName, lastName, email, phone, department, avatar, status, now);
        this.createdAt = now;
    }

    public final void update(String firstName, String lastName, String email, String phone, String department,
            String avatar, UserStatus status, Instant now) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.department = department;
        this.avatar = avatar;
        this.status = status;
        this.updatedAt = now;
    }

    public void moveToBranch(@Nullable UUID branchId, Instant now) {
        this.branchId = branchId;
        this.updatedAt = now;
    }

    public void changeStatus(UserStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /** Signed in or refreshed a token: when they last used the app */
    public void markActive(Instant now) {
        this.lastActiveAt = now;
    }

    /** "Ann Lee", as the activity log names people */
    public String getFullName() {
        return (firstName + " " + lastName).trim();
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getDepartment() {
        return department;
    }

    public @Nullable UUID getBranchId() {
        return branchId;
    }

    public String getAvatar() {
        return avatar;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getLastActiveAt() {
        return lastActiveAt;
    }
}
