package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_CODE;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.TWO_FACTOR_MANDATORY;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.TwoFactorSetupResponse;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import com.adminstarter.accesscontrol.identity.event.TwoFactorChanged;
import com.adminstarter.accesscontrol.identity.policy.SelfProtectionPolicy;
import com.adminstarter.accesscontrol.identity.repository.UserCredentialRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.util.List;
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
    private final UserMapper mapper;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final String issuer;

    public TwoFactorService(UserCredentialRepository credentials, UserAccessService access, TotpService totp,
            UserMapper mapper, SettingsService settings, ApplicationEventPublisher events,
            AccessControlProperties properties) {
        this.credentials = credentials;
        this.access = access;
        this.totp = totp;
        this.mapper = mapper;
        this.settings = settings;
        this.events = events;
        this.issuer = properties.productName();
    }

    /** A new secret, pending until confirmed with a code from it */
    public TwoFactorSetupResponse start(Actor actor) {
        var user = access.find(actor.userId());
        var secret = totp.createSecret();
        load(actor.userId()).startTwoFactor(secret);
        return new TwoFactorSetupResponse(secret, totp.uri(secret, user.getEmail(), issuer));
    }

    public UserResponse confirm(Actor actor, String code) {
        var credential = load(actor.userId());
        var pending = credential.getPendingTwoFactorSecret();
        if (pending == null || !totp.verify(pending, code)) {
            throw INVALID_CODE.exception();
        }
        credential.confirmTwoFactor();
        return changed(actor, actor.userId(), true);
    }

    /** Your own, with a current code; two_factor_mandatory while your role requires it */
    public UserResponse disable(Actor actor, String code) {
        if (actor.roleIds().stream().anyMatch(settings::requiresTwoFactor)) {
            throw TWO_FACTOR_MANDATORY.exception();
        }
        var credential = load(actor.userId());
        var secret = credential.getTwoFactorSecret();
        if (secret == null || !totp.verify(secret, code)) {
            throw INVALID_CODE.exception();
        }
        credential.turnOffTwoFactor();
        return changed(actor, actor.userId(), false);
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
