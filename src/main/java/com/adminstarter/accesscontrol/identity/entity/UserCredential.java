package com.adminstarter.accesscontrol.identity.entity;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * How a user proves who they are, kept apart from the profile: the password hash, the count of wrong attempts, and
 * the two-step sign-in secret. Every user has one row; a user signing in through another identity provider later
 * would simply have no password.
 */
@Entity
@Table(name = "user_credentials")
public class UserCredential {

    @Id
    private UUID userId;

    /** Argon2id; null for someone invited who has not set one yet, or whose password an administrator reset */
    private @Nullable String passwordHash;

    private @Nullable Instant passwordChangedAt;
    private int failedAttempts;
    /** Locked after too many wrong attempts in a row (not the same as suspended, which an administrator decides) */
    private @Nullable Instant lockedUntil;
    /** Base32 TOTP secret; set while two-step sign-in is on. Encrypted in the column. */
    @Convert(converter = EncryptedSecretConverter.class)
    private @Nullable String twoFactorSecret;
    /** A new secret waiting for its first code. Encrypted in the column. */
    @Convert(converter = EncryptedSecretConverter.class)
    private @Nullable String pendingTwoFactorSecret;

    protected UserCredential() {}

    public UserCredential(UUID userId) {
        this.userId = userId;
    }

    public void changePassword(String hash, Instant now) {
        this.passwordHash = hash;
        this.passwordChangedAt = now;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public void clearPassword() {
        this.passwordHash = null;
    }

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** One more wrong attempt; {@code lockUntil} when that is one too many */
    public void failed(@Nullable Instant lockUntil) {
        this.failedAttempts++;
        this.lockedUntil = lockUntil;
    }

    public void succeeded() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public void startTwoFactor(String secret) {
        this.pendingTwoFactorSecret = secret;
    }

    public void confirmTwoFactor() {
        this.twoFactorSecret = pendingTwoFactorSecret;
        this.pendingTwoFactorSecret = null;
    }

    public void turnOffTwoFactor() {
        this.twoFactorSecret = null;
        this.pendingTwoFactorSecret = null;
    }

    public @Nullable String getPasswordHash() {
        return passwordHash;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public @Nullable String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public @Nullable String getPendingTwoFactorSecret() {
        return pendingTwoFactorSecret;
    }

    public boolean isTwoFactorOn() {
        return twoFactorSecret != null;
    }
}
