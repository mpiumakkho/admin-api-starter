package com.adminstarter.accesscontrol.identity.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.EMAIL_TAKEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_INVITED;

import com.adminstarter.accesscontrol.authorization.CorePermissions;
import com.adminstarter.accesscontrol.branch.service.BranchService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import com.adminstarter.accesscontrol.identity.dto.CreateUserRequest;
import com.adminstarter.accesscontrol.identity.dto.DeleteUsersRequest;
import com.adminstarter.accesscontrol.identity.dto.ImportUsersRequest;
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
import com.adminstarter.accesscontrol.identity.policy.BranchScopePolicy;
import com.adminstarter.accesscontrol.identity.policy.SelfProtectionPolicy;
import com.adminstarter.accesscontrol.identity.repository.UserCredentialRepository;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
    private final RoleAssignmentService assignments;
    private final RoleAssignmentResolver resolver;
    private final BranchService branches;
    private final SettingsService settings;
    private final UserMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserLifecycleService(UserRepository users, UserCredentialRepository credentials,
            CredentialServiceImpl credentialService, UserAccessService access, RoleAssignmentService assignments,
            RoleAssignmentResolver resolver, BranchService branches, SettingsService settings, UserMapper mapper,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.credentialService = credentialService;
        this.access = access;
        this.assignments = assignments;
        this.resolver = resolver;
        this.branches = branches;
        this.settings = settings;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    /** Everyone with users.view; with it through branches only, their people and yourself (P-024); otherwise yourself */
    @Transactional(readOnly = true)
    public List<UserResponse> list(Actor actor) {
        var everyone = actor.has(CorePermissions.USERS_VIEW);
        var branches = everyone ? Set.<UUID>of() : BranchScopePolicy.branchesWith(actor, CorePermissions.USERS_VIEW);
        if (!everyone && branches.isEmpty()) {
            return List.of(mapper.toResponse(access.find(actor.userId())));
        }
        var twoFactor = credentials.findTwoFactorUserIds();
        var roles = resolver.byUser();
        return users.findAllByOrderByIdDesc().stream()
                .filter(user -> everyone || actor.is(user.getId()) || (user.getBranchId() != null && branches.contains(user.getBranchId())))
                .map(user -> mapper.toResponse(user, twoFactor.contains(user.getId()), roles.getOrDefault(user.getId(), List.of())))
                .toList();
    }

    /** A new user's roles are roles given: it needs a recent sign-in (P-023) */
    public UserResponse create(Actor actor, CreateUserRequest request) {
        requireCanCreate(actor, request);
        actor.requireRecentSignIn();
        return mapper.toResponse(add(actor, request));
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
        actor.requireRecentSignIn();
        return request.users().stream().map(draft -> mapper.toResponse(add(actor, draft))).toList();
    }

    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public UserResponse update(Actor actor, UUID id, UpdateUserRequest request) {
        var user = access.findManageable(actor, id, CorePermissions.USERS_EDIT);
        var before = Profile.of(user);
        var after = before.with(request);
        var changed = before.changedFields(after);
        // Without users.edit over them, people can still change their own name, phone and photo
        if (!BranchScopePolicy.allows(actor, CorePermissions.USERS_EDIT, before.branchId())) {
            SelfProtectionPolicy.requireSelfEditable(actor, id, changed);
        }
        // Moved only where the actor has users.edit too: a branch administrator keeps people in their branches (P-024)
        if (!Objects.equals(after.branchId(), before.branchId())
                && !BranchScopePolicy.allows(actor, CorePermissions.USERS_EDIT, after.branchId())) {
            throw FORBIDDEN.exception();
        }
        if (request.email() != null) {
            access.requireEmailFree(after.email(), id);
            if (!after.email().equalsIgnoreCase(before.email())) {
                access.requireEmailDomain(after.email());
            }
        }
        // Nobody new joins a closed branch; someone already in it stays
        if (after.branchId() != null && !after.branchId().equals(before.branchId())) {
            branches.requireOpen(after.branchId());
        }
        var statusChanged = after.status() != before.status();
        SelfProtectionPolicy.forbidOwnStatus(actor, id, statusChanged);
        // A status change may take away the last person who can run the system
        assignments.keepingAdministrator(() -> {
            user.update(after.firstName(), after.lastName(), after.email(), after.phone(), after.department(),
                    after.avatar(), after.status(), clock.instant());
            user.moveToBranch(after.branchId(), clock.instant());
            users.flush();
            return user;
        });
        events.publishEvent(new UserUpdated(actor.userId(), id, user.getFullName(), before.diff(after)));
        if (statusChanged && after.status() == UserStatus.SUSPENDED) {
            events.publishEvent(new UserSuspended(id));
        }
        return mapper.toResponse(user);
    }

    /** Suspends or activates several people; answers with the ones that changed */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public List<UserResponse> setStatus(Actor actor, SetUserStatusRequest request) {
        SelfProtectionPolicy.forbidSelf(actor, request.ids());
        var status = UserStatus.of(request.status());
        var targets = request.ids().stream().distinct()
                .map(id -> access.findManageable(actor, id, CorePermissions.USERS_EDIT)).toList();
        var reason = settings.requireReason("status", request.reason());
        var changed = new ArrayList<User>();
        // Suspending may take away the last person who can run the system
        assignments.keepingAdministrator(() -> {
            for (var user : targets) {
                if (user.getStatus() == status) {
                    continue;
                }
                var from = user.getStatus();
                user.changeStatus(status, clock.instant());
                events.publishEvent(new UserStatusChanged(actor.userId(), user.getId(), user.getFullName(), from.value(),
                        status.value(), reason));
                if (status == UserStatus.SUSPENDED) {
                    events.publishEvent(new UserSuspended(user.getId()));
                }
                changed.add(user);
            }
            users.flush();
            return changed;
        });
        return changed.stream().map(mapper::toResponse).toList();
    }

    /** Their sessions, credentials and email links go with them (on delete cascade); the activity log keeps their name */
    @CacheEvict(cacheNames = UserServiceImpl.ACCOUNTS_CACHE, allEntries = true)
    public void delete(Actor actor, DeleteUsersRequest request) {
        SelfProtectionPolicy.forbidSelf(actor, request.ids());
        var targets = request.ids().stream().distinct()
                .map(id -> access.findManageable(actor, id, CorePermissions.USERS_DELETE)).toList();
        actor.requireRecentSignIn();
        var reason = settings.requireReason("delete", request.reason());
        var now = clock.instant();
        for (var user : targets) {
            var roles = resolver.of(user.getId()).stream().filter(item -> item.activeAt(now))
                    .map(item -> item.getRoleId().toString()).distinct().toList();
            events.publishEvent(new UserDeleted(actor.userId(), user.getId(), user.getFullName(), List.of(
                    FieldChange.was("email", user.getEmail()),
                    FieldChange.list("roles", List.of(), roles)), reason));
        }
        // Their role assignments go with them (on delete cascade); that may leave nobody who can run the system
        assignments.keepingAdministrator(() -> {
            users.deleteAll(targets);
            users.flush();
            return targets;
        });
    }

    /**
     * Emails a link to set a new password; for someone else, the old one stops working and their sessions end, and it
     * needs a recent sign-in (P-023) and a reason when the settings say so (P-021)
     */
    public void resetPassword(Actor actor, UUID id, @Nullable String reason) {
        var user = access.findManageable(actor, id, CorePermissions.USERS_EDIT);
        var self = actor.is(id);
        if (!self) {
            actor.requireRecentSignIn();
        }
        var why = self ? "" : settings.requireReason("resetSignIn", reason);
        if (!self) {
            credentialService.clearPassword(id);
        }
        events.publishEvent(new UserPasswordReset(actor.userId(), id, user.getFullName(), user.getEmail(),
                user.getFirstName(), self, why));
    }

    public void resendInvite(Actor actor, UUID id) {
        var user = access.findManageable(actor, id, CorePermissions.USERS_CREATE);
        if (user.getStatus() != UserStatus.INVITED) {
            throw NOT_INVITED.exception();
        }
        events.publishEvent(new UserInvitationResent(actor.userId(), id, user.getFullName(), user.getEmail(),
                user.getFirstName()));
    }

    /** Through branches only, the new user belongs to one of them (P-024) */
    private void requireCanCreate(Actor actor, CreateUserRequest draft) {
        if (!BranchScopePolicy.allows(actor, CorePermissions.USERS_CREATE, draft.branchId())) {
            throw FORBIDDEN.exception();
        }
        if (draft.branchId() != null) {
            branches.requireOpen(draft.branchId());
        }
        assignments.requireCanGive(actor, draft.roles(), draft.branchId());
        access.requireEmailFree(draft.email().trim(), null);
        access.requireEmailDomain(draft.email());
    }

    /** The user, then their first roles, each with its own entry in the log */
    private User add(Actor actor, CreateUserRequest draft) {
        var status = UserStatus.of(draft.status());
        var draftUser = new User(draft.firstName(), draft.lastName(), draft.email().trim(),
                Objects.requireNonNullElse(draft.phone(), ""), draft.department(),
                Objects.requireNonNullElse(draft.avatar(), ""), status, clock.instant());
        draftUser.moveToBranch(draft.branchId(), clock.instant());
        var user = users.save(draftUser);
        credentialService.create(user.getId(), null);
        var changes = new ArrayList<FieldChange>();
        changes.add(FieldChange.set("department", user.getDepartment()));
        if (user.getBranchId() != null) {
            changes.add(FieldChange.set("branchId", user.getBranchId().toString()));
        }
        changes.add(FieldChange.set("email", user.getEmail()));
        changes.add(FieldChange.set("status", status.value()));
        events.publishEvent(new UserCreated(actor.userId(), user.getId(), user.getFullName(), user.getEmail(),
                user.getFirstName(), status == UserStatus.INVITED, List.copyOf(changes)));
        assignments.giveFirstRoles(actor, user, draft.roles());
        return user;
    }

    /** The fields a request may set, before or after a change, in the order the activity log lists them */
    private record Profile(String firstName, String lastName, String email, String phone, String department,
            @Nullable UUID branchId, UserStatus status, String avatar) {

        static Profile of(User user) {
            return new Profile(user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(), user.getDepartment(),
                    user.getBranchId(), user.getStatus(), user.getAvatar());
        }

        Profile with(UpdateUserRequest request) {
            return new Profile(
                    Objects.requireNonNullElse(request.firstName(), firstName),
                    Objects.requireNonNullElse(request.lastName(), lastName),
                    request.email() == null ? email : request.email().trim(),
                    Objects.requireNonNullElse(request.phone(), phone),
                    Objects.requireNonNullElse(request.department(), department),
                    request.branchId() == null ? branchId : request.branchId().orElse(null),
                    request.status() == null ? status : UserStatus.of(request.status()),
                    Objects.requireNonNullElse(request.avatar(), avatar));
        }

        /** Each field as text, null for no branch */
        Map<String, @Nullable String> values() {
            var values = new LinkedHashMap<String, @Nullable String>();
            values.put("firstName", firstName);
            values.put("lastName", lastName);
            values.put("email", email);
            values.put("phone", phone);
            values.put("department", department);
            values.put("branchId", branchId == null ? null : branchId.toString());
            values.put("status", status.value());
            values.put("avatar", avatar);
            return values;
        }

        List<String> changedFields(Profile after) {
            var mine = values();
            var theirs = after.values();
            return mine.keySet().stream().filter(field -> !Objects.equals(mine.get(field), theirs.get(field))).toList();
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
