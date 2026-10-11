package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_CREDENTIALS;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.entity.PreviousPassword;
import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import com.adminstarter.accesscontrol.identity.event.RecoveryCodeUsed;
import com.adminstarter.accesscontrol.identity.event.UserPasswordChanged;
import com.adminstarter.accesscontrol.identity.policy.LockoutPolicy;
import com.adminstarter.accesscontrol.identity.policy.PasswordPolicy;
import com.adminstarter.accesscontrol.identity.repository.PreviousPasswordRepository;
import com.adminstarter.accesscontrol.identity.repository.UserCredentialRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import java.util.List;
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
    private final PreviousPasswordRepository previousPasswords;
    private final UserAccessService access;
    private final SettingsService settings;
    private final PasswordEncoder encoder;
    private final LockoutPolicy lockoutPolicy;
    private final TotpService totp;
    private final RecoveryCodeService recoveryCodes;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Checked against when there is no real hash, to spend the same time */
    private final String decoyHash;

    public CredentialServiceImpl(UserCredentialRepository credentials, PreviousPasswordRepository previousPasswords,
            UserAccessService access, SettingsService settings, PasswordEncoder encoder, LockoutPolicy lockoutPolicy,
            TotpService totp, RecoveryCodeService recoveryCodes, ApplicationEventPublisher events, Clock clock) {
        this.credentials = credentials;
        this.previousPasswords = previousPasswords;
        this.access = access;
        this.settings = settings;
        this.encoder = encoder;
        this.lockoutPolicy = lockoutPolicy;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.events = events;
        this.clock = clock;
        this.decoyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public boolean checkPassword(UUID userId, String password) {
        var credential = credentials.findById(userId).orElse(null);
        var hash = credential == null ? null : credential.getPasswordHash();
        if (credential == null || hash == null || credential.lockedAt(clock.instant())) {
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
        if (secret != null && (totp.verify(secret, code) || usedRecoveryCode(userId, code))) {
            return true;
        }
        failed(credential);
        return false;
    }

    /** One of their recovery codes instead of a code from the app (P-025): used up, and logged with how many are left */
    private boolean usedRecoveryCode(UUID userId, String code) {
        if (!recoveryCodes.use(userId, code)) {
            return false;
        }
        events.publishEvent(new RecoveryCodeUsed(userId, access.find(userId).getFullName(), recoveryCodes.remaining(userId)));
        return true;
    }

    @Override
    public void signInSucceeded(UUID userId) {
        load(userId).succeeded();
    }

    @Override
    public void setPassword(UUID userId, String password) {
        replacePassword(load(userId), password);
        events.publishEvent(new UserPasswordChanged(userId, access.find(userId).getFullName(), null));
    }

    /** POST /me/password: the current password must be right; the other sessions end */
    public void changeOwnPassword(Actor actor, String currentPassword, String newPassword) {
        var credential = load(actor.userId());
        var hash = credential.getPasswordHash();
        if (hash == null || !encoder.matches(currentPassword, hash)) {
            throw INVALID_CREDENTIALS.exception();
        }
        replacePassword(credential, newPassword);
        var fullName = access.find(actor.userId()).getFullName();
        events.publishEvent(new UserPasswordChanged(actor.userId(), fullName, actor.sessionId()));
    }

    /**
     * An administrator reset it: the old password stops working until the user sets a new one from the email. It
     * stays in their history, so the new one cannot be the same (API.md, "Password rules").
     */
    void clearPassword(UUID userId) {
        var credential = load(userId);
        keepInHistory(credential, previousPasswords.findByUserIdOrderByCreatedAtDescIdDesc(userId));
        credential.clearPassword();
    }

    /** A user created on the dashboard has no password until they accept the invitation or reset it */
    void create(UUID userId, @Nullable String password) {
        var credential = new UserCredential(userId);
        if (password != null) {
            credential.changePassword(encoder.encode(password), clock.instant());
        }
        credentials.save(credential);
    }

    /**
     * The password rules of the settings (weak_password, password_reused), then the new password; the one it
     * replaces goes into the history.
     */
    private void replacePassword(UserCredential credential, String password) {
        var rules = settings.current();
        PasswordPolicy.check(password, rules.passwordMinLength());
        var earlier = previousPasswords.findByUserIdOrderByCreatedAtDescIdDesc(credential.getUserId());
        var recent = PasswordPolicy.recentHashes(credential.getPasswordHash(),
                earlier.stream().map(PreviousPassword::getPasswordHash).toList(), rules.passwordHistory());
        PasswordPolicy.checkNotReused(password, recent, encoder::matches);
        keepInHistory(credential, earlier);
        credential.changePassword(encoder.encode(password), clock.instant());
    }

    /**
     * Moves the current password, if there is one, into the history, dropping the oldest beyond PasswordPolicy.KEPT.
     * Kept whatever passwordHistory is now, so raising it later counts the passwords used meanwhile.
     */
    private void keepInHistory(UserCredential credential, List<PreviousPassword> earlier) {
        var hash = credential.getPasswordHash();
        if (hash == null) {
            return;
        }
        previousPasswords.deleteAll(PasswordPolicy.beyondKept(earlier));
        previousPasswords.save(new PreviousPassword(credential.getUserId(), hash, clock.instant()));
    }

    private void failed(UserCredential credential) {
        var lockUntil = lockoutPolicy.lockFor(credential.getFailedAttempts() + 1).map(clock.instant()::plus).orElse(null);
        credential.failed(lockUntil);
    }

    private UserCredential load(UUID userId) {
        return credentials.findById(userId).orElseGet(() -> credentials.save(new UserCredential(userId)));
    }

}
