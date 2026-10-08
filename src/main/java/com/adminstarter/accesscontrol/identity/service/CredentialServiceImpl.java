package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_CREDENTIALS;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import com.adminstarter.accesscontrol.identity.event.UserPasswordChanged;
import com.adminstarter.accesscontrol.identity.policy.LockoutPolicy;
import com.adminstarter.accesscontrol.identity.policy.PasswordPolicy;
import com.adminstarter.accesscontrol.identity.repository.UserCredentialRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CredentialServiceImpl implements CredentialService {

    private final UserCredentialRepository credentials;
    private final UserAccessService access;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final LockoutPolicy lockoutPolicy;
    private final TotpService totp;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Checked against when there is no real hash, to spend the same time */
    private final String decoyHash;

    public CredentialServiceImpl(UserCredentialRepository credentials, UserAccessService access, PasswordEncoder encoder,
            PasswordPolicy passwordPolicy, LockoutPolicy lockoutPolicy, TotpService totp,
            ApplicationEventPublisher events, Clock clock) {
        this.credentials = credentials;
        this.access = access;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.lockoutPolicy = lockoutPolicy;
        this.totp = totp;
        this.events = events;
        this.clock = clock;
        this.decoyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public boolean checkPassword(UUID userId, String password) {
        var credential = credentials.findById(userId).orElse(null);
        var hash = credential == null ? null : credential.getPasswordHash();
        if (credential == null || hash == null || credential.lockedAt(now())) {
            checkNoPassword(password);
            return false;
        }
        if (encoder.matches(password, hash)) {
            return true;
        }
        failed(credential);
        return false;
    }

    @Override
    public void checkNoPassword(String password) {
        encoder.matches(password, decoyHash);
    }

    @Override
    public boolean checkTwoFactorCode(UUID userId, String code) {
        var credential = load(userId);
        var secret = credential.getTwoFactorSecret();
        if (secret != null && totp.verify(secret, code)) {
            return true;
        }
        failed(credential);
        return false;
    }

    @Override
    public void signInSucceeded(UUID userId) {
        load(userId).succeeded();
    }

    @Override
    public void setPassword(UUID userId, String password) {
        passwordPolicy.check(password);
        load(userId).changePassword(encoder.encode(password), now());
        events.publishEvent(new UserPasswordChanged(userId, access.find(userId).getFullName(), null));
    }

    /** POST /me/password: the current password must be right; the other sessions end */
    public void changeOwnPassword(Actor actor, String currentPassword, String newPassword) {
        var credential = load(actor.userId());
        var hash = credential.getPasswordHash();
        if (hash == null || !encoder.matches(currentPassword, hash)) {
            throw INVALID_CREDENTIALS.exception();
        }
        passwordPolicy.check(newPassword);
        credential.changePassword(encoder.encode(newPassword), now());
        var fullName = access.find(actor.userId()).getFullName();
        events.publishEvent(new UserPasswordChanged(actor.userId(), fullName, actor.sessionId()));
    }

    /** An administrator reset it: the old password stops working until the user sets a new one from the email */
    void clearPassword(UUID userId) {
        load(userId).clearPassword();
    }

    /** A user created on the dashboard has no password until they accept the invitation or reset it */
    void create(UUID userId, @Nullable String password) {
        var credential = new UserCredential(userId);
        if (password != null) {
            credential.changePassword(encoder.encode(password), now());
        }
        credentials.save(credential);
    }

    private void failed(UserCredential credential) {
        var lockUntil = lockoutPolicy.lockFor(credential.getFailedAttempts() + 1).map(now()::plus).orElse(null);
        credential.failed(lockUntil);
    }

    private UserCredential load(UUID userId) {
        return credentials.findById(userId).orElseGet(() -> credentials.save(new UserCredential(userId)));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
