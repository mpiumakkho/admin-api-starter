package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.EMAIL_TAKEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_INVITED;

import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.identity.dto.CreateUserRequest;
import com.adminstarter.accesscontrol.identity.dto.DeleteUsersRequest;
import com.adminstarter.accesscontrol.identity.dto.ImportUsersRequest;
import com.adminstarter.accesscontrol.identity.dto.SetUserRoleRequest;
import com.adminstarter.accesscontrol.identity.dto.SetUserStatusRequest;
import com.adminstarter.accesscontrol.identity.dto.UpdateUserRequest;
import com.adminstarter.accesscontrol.identity.dto.UserResponse;
import com.adminstarter.accesscontrol.identity.entity.User;
import com.adminstarter.accesscontrol.identity.entity.UserStatus;
import com.adminstarter.accesscontrol.identity.event.UserCreated;
import com.adminstarter.accesscontrol.identity.event.UserDeleted;
import com.adminstarter.accesscontrol.identity.event.UserInvitationResent;
import com.adminstarter.accesscontrol.identity.event.UserPasswordReset;
import com.adminstarter.accesscontrol.identity.event.UserStatusChanged;
import com.adminstarter.accesscontrol.identity.event.UserSuspended;
import com.adminstarter.accesscontrol.identity.event.UserUpdated;
import com.adminstarter.accesscontrol.identity.policy.SelfProtectionPolicy;
import com.adminstarter.accesscontrol.identity.repository.UserCredentialRepository;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything the users pages change, with the rules in API.md; local-admin-api.ts is the reference */
@Service
@Transactional
public class UserLifecycleService {

    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final CredentialServiceImpl credentialService;
    private final UserAccessService access;
    private final RoleService roles;
    private final UserMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserLifecycleService(UserRepository users, UserCredentialRepository credentials,
            CredentialServiceImpl credentialService, UserAccessService access, RoleService roles, UserMapper mapper,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.credentialService = credentialService;
        this.access = access;
        this.roles = roles;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    /** Everyone, or only yourself without users.view */
    @Transactional(readOnly = true)
    public List<UserResponse> list(Actor actor) {
        if (!actor.has(CorePermissions.USERS_VIEW)) {
            return List.of(respond(access.find(actor.userId())));
        }
        var twoFactor = credentials.findTwoFactorUserIds();
        return users.findAllByOrderByIdDesc().stream()
                .map(user -> mapper.toResponse(user, twoFactor.contains(user.getId())))
                .toList();
    }

    public UserResponse create(Actor actor, CreateUserRequest request) {
        requireCanCreate(actor, request);
        return respond(add(actor, request));
    }

    /** All or none: every row is checked before any is added, and one refusal refuses the whole import */
    public List<UserResponse> importUsers(Actor actor, ImportUsersRequest request) {
        var emails = new HashSet<String>();
        for (var draft : request.users()) {
            requireCanCreate(actor, draft);
            if (!emails.add(draft.email().trim().toLowerCase(Locale.ROOT))) {
                throw EMAIL_TAKEN.exception();
            }
        }
        return request.users().stream().map(draft -> respond(add(actor, draft))).toList();
    }

    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public UserResponse update(Actor actor, UUID id, UpdateUserRequest request) {
        var user = access.findManageable(actor, id);
        var before = Profile.of(user);
        var after = before.with(request);
        var changed = before.changedFields(after);
        // Without users.edit, people can still change their own name, phone and photo
        if (!actor.has(CorePermissions.USERS_EDIT)) {
            SelfProtectionPolicy.requireSelfEditable(actor, id, changed);
        }
        if (request.email() != null) {
            access.requireEmailFree(after.email(), id);
            if (!after.email().equalsIgnoreCase(before.email())) {
                access.requireEmailDomain(after.email());
            }
        }
        var roleChanged = !after.roleId().equals(before.roleId());
        var statusChanged = after.status() != before.status();
        SelfProtectionPolicy.forbidOwnRoleOrStatus(actor, id, roleChanged, statusChanged);
        if (roleChanged) {
            roles.requireAssignable(actor, after.roleId());
        }
        user.update(after.firstName(), after.lastName(), after.email(), after.phone(), after.department(),
                after.avatar(), after.roleId(), after.status(), now());
        events.publishEvent(new UserUpdated(actor.userId(), id, user.getFullName(), before.diff(after)));
        if (statusChanged && after.status() == UserStatus.SUSPENDED) {
            events.publishEvent(new UserSuspended(id));
        }
        return respond(user);
    }

    /** Suspends or activates several people; answers with the ones that changed */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public List<UserResponse> setStatus(Actor actor, SetUserStatusRequest request) {
        SelfProtectionPolicy.forbidSelf(actor, request.ids());
        var status = UserStatus.of(request.status());
        var targets = request.ids().stream().distinct().map(id -> access.findManageable(actor, id)).toList();
        var changed = new ArrayList<UserResponse>();
        for (var user : targets) {
            if (user.getStatus() == status) {
                continue;
            }
            var from = user.getStatus();
            user.changeStatus(status, now());
            events.publishEvent(new UserStatusChanged(actor.userId(), user.getId(), user.getFullName(), from.value(),
                    status.value()));
            if (status == UserStatus.SUSPENDED) {
                events.publishEvent(new UserSuspended(user.getId()));
            }
            changed.add(respond(user));
        }
        return changed;
    }

    /**
     * Gives several people the same role; answers with the ones whose role changed. Nobody changes their own role,
     * assigns a role that grants more than they hold, or changes someone they may not manage (API.md, rules 2 to 4).
     */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public List<UserResponse> setRole(Actor actor, SetUserRoleRequest request) {
        SelfProtectionPolicy.forbidSelf(actor, request.ids());
        roles.requireAssignable(actor, request.roleId());
        var targets = request.ids().stream().distinct().map(id -> access.findManageable(actor, id)).toList();
        var changed = new ArrayList<UserResponse>();
        for (var user : targets) {
            if (user.getRoleId().equals(request.roleId())) {
                continue;
            }
            var from = user.getRoleId();
            user.changeRole(request.roleId(), now());
            events.publishEvent(new UserUpdated(actor.userId(), user.getId(), user.getFullName(),
                    List.of(FieldChange.of("roleId", from.toString(), request.roleId().toString()))));
            changed.add(respond(user));
        }
        return changed;
    }

    /** Their sessions, credentials and email links go with them (on delete cascade); the activity log keeps their name */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public void delete(Actor actor, DeleteUsersRequest request) {
        SelfProtectionPolicy.forbidSelf(actor, request.ids());
        var targets = request.ids().stream().distinct().map(id -> access.findManageable(actor, id)).toList();
        for (var user : targets) {
            events.publishEvent(new UserDeleted(actor.userId(), user.getId(), user.getFullName(), List.of(
                    FieldChange.was("email", user.getEmail()),
                    FieldChange.was("roleId", user.getRoleId().toString()))));
        }
        users.deleteAll(targets);
    }

    /** Emails a link to set a new password; for someone else, the old one stops working and their sessions end */
    public void resetPassword(Actor actor, UUID id) {
        var user = access.findManageable(actor, id);
        var self = actor.is(id);
        if (!self) {
            credentialService.clearPassword(id);
        }
        events.publishEvent(new UserPasswordReset(actor.userId(), id, user.getFullName(), user.getEmail(),
                user.getFirstName(), self));
    }

    public void resendInvite(Actor actor, UUID id) {
        var user = access.findManageable(actor, id);
        if (user.getStatus() != UserStatus.INVITED) {
            throw NOT_INVITED.exception();
        }
        events.publishEvent(new UserInvitationResent(actor.userId(), id, user.getFullName(), user.getEmail(),
                user.getFirstName()));
    }

    private void requireCanCreate(Actor actor, CreateUserRequest draft) {
        roles.requireAssignable(actor, draft.roleId());
        access.requireEmailFree(draft.email().trim(), null);
        access.requireEmailDomain(draft.email());
    }

    private User add(Actor actor, CreateUserRequest draft) {
        var status = UserStatus.of(draft.status());
        var user = users.save(new User(draft.firstName(), draft.lastName(), draft.email().trim(),
                Objects.requireNonNullElse(draft.phone(), ""), draft.department(),
                Objects.requireNonNullElse(draft.avatar(), ""), draft.roleId(), status, now()));
        credentialService.create(user.getId(), null);
        events.publishEvent(new UserCreated(actor.userId(), user.getId(), user.getFullName(), user.getEmail(),
                user.getFirstName(), status == UserStatus.INVITED, List.of(
                        FieldChange.set("roleId", user.getRoleId().toString()),
                        FieldChange.set("department", user.getDepartment()),
                        FieldChange.set("email", user.getEmail()),
                        FieldChange.set("status", status.value()))));
        return user;
    }

    private UserResponse respond(User user) {
        return mapper.toResponse(user, credentials.findById(user.getId()).map(c -> c.isTwoFactorOn()).orElse(false));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** The fields a request may set, before or after a change, in the order the activity log lists them */
    private record Profile(String firstName, String lastName, String email, String phone, String department,
            UUID roleId, UserStatus status, String avatar) {

        static Profile of(User user) {
            return new Profile(user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(), user.getDepartment(),
                    user.getRoleId(), user.getStatus(), user.getAvatar());
        }

        Profile with(UpdateUserRequest request) {
            return new Profile(
                    Objects.requireNonNullElse(request.firstName(), firstName),
                    Objects.requireNonNullElse(request.lastName(), lastName),
                    request.email() == null ? email : request.email().trim(),
                    Objects.requireNonNullElse(request.phone(), phone),
                    Objects.requireNonNullElse(request.department(), department),
                    Objects.requireNonNullElse(request.roleId(), roleId),
                    request.status() == null ? status : UserStatus.of(request.status()),
                    Objects.requireNonNullElse(request.avatar(), avatar));
        }

        Map<String, String> values() {
            var values = new LinkedHashMap<String, String>();
            values.put("firstName", firstName);
            values.put("lastName", lastName);
            values.put("email", email);
            values.put("phone", phone);
            values.put("department", department);
            values.put("roleId", roleId.toString());
            values.put("status", status.value());
            values.put("avatar", avatar);
            return values;
        }

        List<String> changedFields(Profile after) {
            var mine = values();
            var theirs = after.values();
            return mine.keySet().stream().filter(field -> !mine.get(field).equals(theirs.get(field))).toList();
        }

        List<FieldChange> diff(Profile after) {
            var mine = values();
            var theirs = after.values();
            return changedFields(after).stream()
                    // Photos are data URLs: too big to keep, so the log only notes that it changed
                    .map(field -> field.equals("avatar") ? FieldChange.changed(field)
                            : FieldChange.of(field, mine.get(field), theirs.get(field)))
                    .toList();
        }
    }
}
