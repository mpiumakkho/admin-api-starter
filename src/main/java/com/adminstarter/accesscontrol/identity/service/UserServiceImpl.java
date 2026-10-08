package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import com.adminstarter.accesscontrol.identity.entity.UserStatus;
import com.adminstarter.accesscontrol.identity.event.UserStatusChanged;
import com.adminstarter.accesscontrol.identity.repository.UserCredentialRepository;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    /** findAccount; evicted, after commit, whenever a user's status or role may have changed */
    static final String ACCOUNTS_CACHE = "accounts";

    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final UserAccessService access;
    private final UserMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserServiceImpl(UserRepository users, UserCredentialRepository credentials, UserAccessService access,
            UserMapper mapper, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.access = access;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ACCOUNTS_CACHE, unless = "#result == null")
    public Optional<AccountSummary> findAccount(UUID userId) {
        return users.findById(userId).map(user -> mapper.toAccount(user, twoFactor(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummary> findAccountByEmail(String email) {
        return users.findByEmail(email.trim()).map(user -> mapper.toAccount(user, twoFactor(user.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return mapper.toResponse(access.find(userId), twoFactor(userId));
    }

    @Override
    public void recordSignIn(UUID userId) {
        access.find(userId).signedIn(clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    @CacheEvict(cacheNames = ACCOUNTS_CACHE, allEntries = true)
    public void acceptInvitation(UUID userId) {
        var user = access.find(userId);
        if (user.getStatus() == UserStatus.INVITED) {
            user.changeStatus(UserStatus.ACTIVE, clock.instant().truncatedTo(ChronoUnit.MILLIS));
            events.publishEvent(new UserStatusChanged(userId, userId, user.getFullName(), UserStatus.INVITED.value(),
                    UserStatus.ACTIVE.value()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse requireSecurityManageable(Actor actor, UUID userId) {
        var user = access.findSecurityManageable(actor, userId);
        return mapper.toResponse(user, twoFactor(userId));
    }

    private boolean twoFactor(UUID userId) {
        return credentials.findById(userId).map(UserCredential::isTwoFactorOn).orElse(false);
    }
}
