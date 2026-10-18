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
    /**
     * Wrong attempts from where the person never signed in, and the lock they lead to (not the same as suspended,
     * which an administrator decides); the familiar ones below count apart, so a stranger's guesses do not lock the
     * person out where they usually sign in (P-035)
     */
    private int failedAttempts;
    private @Nullable Instant lockedUntil;
    private @Nullable Instant lastFailedAt;
    /** The same, from an address the person signed in from before, and after signing in (P-035) */
    private int familiarFailedAttempts;
    private @Nullable Instant familiarLockedUntil;
    private @Nullable Instant familiarLastFailedAt;
    /** Base32 TOTP secret; set while two-step sign-in is on. Encrypted in the column. */
    @Convert(converter = EncryptedSecretConverter.class)
    private @Nullable String twoFactorSecret;
    /** The step of the last code from the app that was accepted: no code of that step or before works again (P-036) */
    private @Nullable Long lastTotpStep;
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
        succeeded(true);
        succeeded(false);
    }

    public void clearPassword() {
        this.passwordHash = null;
    }

    public boolean lockedAt(Instant now, boolean familiar) {
        var until = familiar ? familiarLockedUntil : lockedUntil;
        return until != null && now.isBefore(until);
    }

    /** The wrong attempts in a row so far, from here or elsewhere; none once the last was before {@code since} */
    public int failedAttemptsSince(Instant since, boolean familiar) {
        var last = familiar ? familiarLastFailedAt : lastFailedAt;
        return last == null || last.isBefore(since) ? 0 : familiar ? familiarFailedAttempts : failedAttempts;
    }

    /** One more wrong attempt, the {@code attempts}-th in a row; {@code lockUntil} when that is one too many */
    public void failed(boolean familiar, int attempts, Instant now, @Nullable Instant lockUntil) {
        if (familiar) {
            this.familiarFailedAttempts = attempts;
            this.familiarLastFailedAt = now;
            this.familiarLockedUntil = lockUntil;
        } else {
            this.failedAttempts = attempts;
            this.lastFailedAt = now;
            this.lockedUntil = lockUntil;
        }
    }

    /** The right answer, from here or elsewhere: that count starts again */
    public void succeeded(boolean familiar) {
        if (familiar) {
            this.familiarFailedAttempts = 0;
            this.familiarLastFailedAt = null;
            this.familiarLockedUntil = null;
        } else {
            this.failedAttempts = 0;
            this.lastFailedAt = null;
            this.lockedUntil = null;
        }
    }

    public void startTwoFactor(String secret) {
        this.pendingTwoFactorSecret = secret;
    }

    /** On, with the step of the code that confirmed it, which then works no more */
    public void confirmTwoFactor(long step) {
        this.twoFactorSecret = pendingTwoFactorSecret;
        this.pendingTwoFactorSecret = null;
        this.lastTotpStep = step;
    }

    public void turnOffTwoFactor() {
        this.twoFactorSecret = null;
        this.pendingTwoFactorSecret = null;
        this.lastTotpStep = null;
    }

    /** A code of this step was accepted, as the database already says (UserCredentialRepository.useTotpStep) */
    public void usedTotpStep(long step) {
        this.lastTotpStep = step;
    }

    public UUID getUserId() {
        return userId;
    }

    public @Nullable String getPasswordHash() {
        return passwordHash;
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
