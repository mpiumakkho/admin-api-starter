package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.EMAIL_DOMAIN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.EMAIL_TAKEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.entity.User;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** The checks every change to a user goes through (API.md, rules 2 and 3) */
@Service
public class UserAccessService {

    private final UserRepository users;
    private final RoleService roles;
    private final SettingsService settings;

    public UserAccessService(UserRepository users, RoleService roles, SettingsService settings) {
        this.users = users;
        this.roles = roles;
        this.settings = settings;
    }

    public User find(UUID userId) {
        return users.findById(userId).orElseThrow(NOT_FOUND::exception);
    }

    /** A user the actor may change: nobody manages someone whose role allows more than their own */
    public User findManageable(Actor actor, UUID userId) {
        var user = find(userId);
        if (!actor.is(userId) && !roles.canManageHolderOf(actor, user.getRoleId())) {
            throw FORBIDDEN.exception();
        }
        return user;
    }

    /** Your own sessions and two-step sign-in, or, with users.edit, those of someone you manage */
    public User findSecurityManageable(Actor actor, UUID userId) {
        if (actor.is(userId)) {
            return find(userId);
        }
        if (!actor.has(CorePermissions.USERS_EDIT)) {
            throw FORBIDDEN.exception();
        }
        return findManageable(actor, userId);
    }

    public void requireEmailFree(String email, @Nullable UUID exceptUserId) {
        var taken = exceptUserId == null ? users.emailTaken(email) : users.emailTakenByOther(email, exceptUserId);
        if (taken) {
            throw EMAIL_TAKEN.exception();
        }
    }

    /** When the settings list allowed domains, the email must end in one of them */
    public void requireEmailDomain(String email) {
        var domains = settings.current().allowedDomains();
        var value = email.trim().toLowerCase(Locale.ROOT);
        if (!domains.isEmpty() && !domains.contains(value.substring(value.lastIndexOf('@') + 1))) {
            throw EMAIL_DOMAIN.exception();
        }
    }
}
