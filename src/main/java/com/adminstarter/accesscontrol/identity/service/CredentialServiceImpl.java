package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_CREDENTIALS;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.exception.AccessControlException;
import com.adminstarter.accesscontrol.identity.entity.PreviousPassword;
import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import com.adminstarter.accesscontrol.identity.event.AccountLocked;
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
    public boolean checkPassword(UUID userId, String password, boolean familiar) {
        var credential = credentials.findById(userId).orElse(null);
        var hash = credential == null ? null : credential.getPasswordHash();
        if (credential == null || hash == null || credential.lockedAt(clock.instant(), familiar)) {
            checkNoPassword(password);
            return false;
        }
        if (encoder.matches(password, hash)) {
            return true;
        }
        failed(credential, familiar);
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean lockedNow(UUID userId, boolean familiar) {
        return credentials.findById(userId).map(credential -> credential.lockedAt(clock.instant(), familiar)).orElse(false);
    }

    @Override
    public void checkNoPassword(String password) {
        encoder.matches(password, decoyHash);
    }

    @Override
    public boolean checkTwoFactorCode(UUID userId, String code, boolean familiar) {
        var credential = load(userId);
        // Locked: no code is checked, as no password is (P-026)
        if (credential.lockedAt(clock.instant(), familiar)) {
            return false;
        }
        var secret = credential.getTwoFactorSecret();
        if (secret != null && (totp.verify(secret, code) || usedRecoveryCode(userId, code))) {
            return true;
        }
        failed(credential, familiar);
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
    public void signInSucceeded(UUID userId, boolean familiar) {
        load(userId).succeeded(familiar);
    }

    @Override
    public void setPassword(UUID userId, String password) {
        replacePassword(load(userId), password);
        events.publishEvent(new UserPasswordChanged(userId, access.find(userId).getFullName(), null));
    }

    /**
     * POST /me/password: the current password must be right, and a wrong one counts towards the lockout as at
     * sign-in, its count committed with the refusal (noRollbackFor); the other sessions end
     */
    @Transactional(noRollbackFor = AccessControlException.class)
    public void changeOwnPassword(Actor actor, String currentPassword, String newPassword) {
        if (!checkPassword(actor.userId(), currentPassword)) {
            throw INVALID_CREDENTIALS.exception();
        }
        var credential = load(actor.userId());
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

    /**
     * One more wrong answer in a row, from where the person signed in before or from elsewhere (P-035), the count
     * starting again when the last was long enough ago; the one that locks the account is logged (P-026)
     */
    private void failed(UserCredential credential, boolean familiar) {
        var now = clock.instant();
        var attempts = credential.failedAttemptsSince(now.minus(lockoutPolicy.resetAfter()), familiar) + 1;
        var lockFor = lockoutPolicy.lockFor(attempts);
        credential.failed(familiar, attempts, now, lockFor.map(now::plus).orElse(null));
        lockFor.ifPresent(duration -> events.publishEvent(new AccountLocked(credential.getUserId(),
                access.find(credential.getUserId()).getFullName(), duration.toMinutes())));
    }

    private UserCredential load(UUID userId) {
        return credentials.findById(userId).orElseGet(() -> credentials.save(new UserCredential(userId)));
    }

}
