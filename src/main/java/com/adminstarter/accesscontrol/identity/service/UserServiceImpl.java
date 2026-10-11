package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.entity.UserStatus;
import com.adminstarter.accesscontrol.identity.event.UserStatusChanged;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import java.time.Clock;
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
    private final UserAccessService access;
    private final UserMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserServiceImpl(UserRepository users, UserAccessService access, UserMapper mapper,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.access = access;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ACCOUNTS_CACHE, unless = "#result == null")
    public Optional<AccountSummary> findAccount(UUID userId) {
        return users.findById(userId).map(mapper::toAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummary> findAccountByEmail(String email) {
        return users.findByEmail(email.trim()).map(mapper::toAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return mapper.toResponse(access.find(userId));
    }

    @Override
    public void recordSignIn(UUID userId) {
        access.find(userId).signedIn(clock.instant());
    }

    @Override
    @CacheEvict(cacheNames = ACCOUNTS_CACHE, allEntries = true)
    public void acceptInvitation(UUID userId) {
        var user = access.find(userId);
        if (user.getStatus() == UserStatus.INVITED) {
            user.changeStatus(UserStatus.ACTIVE, clock.instant());
            events.publishEvent(new UserStatusChanged(userId, userId, user.getFullName(), UserStatus.INVITED.value(),
                    UserStatus.ACTIVE.value(), ""));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse requireSecurityManageable(Actor actor, UUID userId) {
        var user = access.findSecurityManageable(actor, userId);
        return mapper.toResponse(user);
    }
}
