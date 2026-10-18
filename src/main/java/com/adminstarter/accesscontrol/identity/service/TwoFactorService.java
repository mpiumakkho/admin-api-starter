package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_CODE;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_REQUEST;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.TWO_FACTOR_MANDATORY;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.exception.AccessControlException;
import com.adminstarter.accesscontrol.identity.dto.RecoveryCodesLeftResponse;
import com.adminstarter.accesscontrol.identity.dto.RecoveryCodesResponse;
import com.adminstarter.accesscontrol.identity.dto.TwoFactorConfirmationResponse;
import com.adminstarter.accesscontrol.identity.dto.TwoFactorSetupResponse;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import com.adminstarter.accesscontrol.identity.event.RecoveryCodesCreated;
import com.adminstarter.accesscontrol.identity.event.TwoFactorChanged;
import com.adminstarter.accesscontrol.identity.policy.SelfProtectionPolicy;
import com.adminstarter.accesscontrol.identity.repository.UserCredentialRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step sign-in with an authenticator app. People turn it on and off for themselves, with a code, except that
 * nobody turns it off while their role requires it (P-016); with users.edit they can turn it off for someone they
 * manage who lost their phone, but never on.
 */
@Service
@Transactional
@CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
public class TwoFactorService {

    private final UserCredentialRepository credentials;
    private final UserAccessService access;
    private final TotpService totp;
    private final CredentialServiceImpl credentialService;
    private final RecoveryCodeService recoveryCodes;
    private final UserMapper mapper;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final String issuer;

    public TwoFactorService(UserCredentialRepository credentials, UserAccessService access, TotpService totp,
            CredentialServiceImpl credentialService, RecoveryCodeService recoveryCodes, UserMapper mapper,
            SettingsService settings, ApplicationEventPublisher events, AccessControlProperties properties) {
        this.credentials = credentials;
        this.access = access;
        this.totp = totp;
        this.credentialService = credentialService;
        this.recoveryCodes = recoveryCodes;
        this.mapper = mapper;
        this.settings = settings;
        this.events = events;
        this.issuer = properties.productName();
    }

    /**
     * A new secret, pending until confirmed with a code from it. Not while two-step sign-in is on: replacing the
     * authenticator would take the account from whoever has the access token, so it is turned off first, with a code.
     * A risky change (P-023): a stolen token could otherwise bind someone else's account to its own authenticator.
     */
    public TwoFactorSetupResponse start(Actor actor) {
        var credential = load(actor.userId());
        if (credential.getTwoFactorSecret() != null) {
            throw INVALID_REQUEST.exception();
        }
        actor.requireRecentSignIn();
        var user = access.find(actor.userId());
        var secret = totp.createSecret();
        credential.startTwoFactor(secret);
        return new TwoFactorSetupResponse(secret, totp.uri(secret, user.getEmail(), issuer));
    }

    /** On, with its first recovery codes (P-025) */
    public TwoFactorConfirmationResponse confirm(Actor actor, String code) {
        var credential = load(actor.userId());
        if (credential.getTwoFactorSecret() != null) {
            throw INVALID_REQUEST.exception();
        }
        var pending = credential.getPendingTwoFactorSecret();
        var step = pending == null ? OptionalLong.empty() : totp.matchedStep(pending, code);
        if (step.isEmpty()) {
            throw INVALID_CODE.exception();
        }
        // The code that turned it on does not sign in again (P-036)
        credential.confirmTwoFactor(step.getAsLong());
        var codes = recoveryCodes.replace(actor.userId());
        return new TwoFactorConfirmationResponse(changed(actor, actor.userId(), true), codes);
    }

    /**
     * Your own, with a current code or a recovery code, wrong ones counting towards the lockout; two_factor_mandatory
     * while your role requires it. The recovery codes go with it. A wrong code still commits what it counted
     * (noRollbackFor), as at sign-in, or six digits could be guessed here without end.
     */
    @Transactional(noRollbackFor = AccessControlException.class)
    public UserResponse disable(Actor actor, String code) {
        if (actor.roleIds().stream().anyMatch(settings::requiresTwoFactor)) {
            throw TWO_FACTOR_MANDATORY.exception();
        }
        var credential = load(actor.userId());
        if (credential.getTwoFactorSecret() == null || !credentialService.checkTwoFactorCode(actor.userId(), code)) {
            throw INVALID_CODE.exception();
        }
        credential.turnOffTwoFactor();
        recoveryCodes.deleteAll(actor.userId());
        return changed(actor, actor.userId(), false);
    }

    /** How many of your recovery codes are left */
    @Transactional(readOnly = true)
    public RecoveryCodesLeftResponse recoveryCodesLeft(Actor actor) {
        return new RecoveryCodesLeftResponse(recoveryCodes.remaining(actor.userId()));
    }

    /**
     * Ten new recovery codes for yourself, which replace the old ones: a risky change, as they get past two-step
     * sign-in (P-023); invalid_request while it is off
     */
    public RecoveryCodesResponse newRecoveryCodes(Actor actor) {
        if (load(actor.userId()).getTwoFactorSecret() == null) {
            throw INVALID_REQUEST.exception();
        }
        actor.requireRecentSignIn();
        var codes = recoveryCodes.replace(actor.userId());
        events.publishEvent(new RecoveryCodesCreated(actor.userId(), access.find(actor.userId()).getFullName()));
        return new RecoveryCodesResponse(codes);
    }

    /**
     * Someone else's, for a lost phone; your own goes through disable, with a code. Allowed even when their role
     * requires it: they then have to turn it on again before anything else.
     */
    public UserResponse reset(Actor actor, UUID userId, @Nullable String reason) {
        SelfProtectionPolicy.forbidSelf(actor, List.of(userId));
        access.findManageable(actor, userId, CorePermissions.USERS_EDIT);
        actor.requireRecentSignIn();
        var why = settings.requireReason("resetSignIn", reason);
        load(userId).turnOffTwoFactor();
        recoveryCodes.deleteAll(userId);
        return changed(actor, userId, false, why);
    }

    private UserResponse changed(Actor actor, UUID userId, boolean enabled) {
        return changed(actor, userId, enabled, "");
    }

    private UserResponse changed(Actor actor, UUID userId, boolean enabled, String reason) {
        var user = access.find(userId);
        events.publishEvent(new TwoFactorChanged(actor.userId(), userId, user.getFullName(), enabled, reason));
        return mapper.toResponse(user, enabled);
    }

    private UserCredential load(UUID userId) {
        return credentials.findById(userId).orElseGet(() -> credentials.save(new UserCredential(userId)));
    }
}
