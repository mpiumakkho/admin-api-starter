package com.adminstarter.accesscontrol.authorization.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_REQUEST;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.authorization.dto.CreateRoleConflictRequest;
import com.adminstarter.accesscontrol.authorization.dto.RoleConflictResponse;
import com.adminstarter.accesscontrol.authorization.dto.UpdateRoleConflictRequest;
import com.adminstarter.accesscontrol.authorization.entity.RoleConflict;
import com.adminstarter.accesscontrol.authorization.event.RoleConflictCreated;
import com.adminstarter.accesscontrol.authorization.event.RoleConflictDeleted;
import com.adminstarter.accesscontrol.authorization.event.RoleConflictUpdated;
import com.adminstarter.accesscontrol.authorization.repository.RoleConflictRepository;
import com.adminstarter.accesscontrol.authorization.repository.RoleRepository;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conflicting roles (P-022): the rules the roles page keeps (roles.manage), and what identity asks before giving a role.
 * API.md, "Conflicting roles"; local-admin-api.ts is the reference.
 */
@Service
@Transactional
public class RoleConflictService {

    private final RoleConflictRepository conflicts;
    private final RoleRepository roles;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoleConflictService(RoleConflictRepository conflicts, RoleRepository roles, ApplicationEventPublisher events,
            Clock clock) {
        this.conflicts = conflicts;
        this.roles = roles;
        this.events = events;
        this.clock = clock;
    }

    /** Everyone signed in reads them, to explain a refusal */
    @Transactional(readOnly = true)
    public List<RoleConflictResponse> list() {
        return conflicts.findAllByOrderByIdAsc().stream().map(RoleConflictService::toResponse).toList();
    }

    /** The rules that name a role: giving it to someone who holds another role of one of them is role_conflict */
    @Transactional(readOnly = true)
    public List<RoleConflictResponse> rulesWith(UUID roleId) {
        return conflicts.findWithRole(roleId).stream().map(RoleConflictService::toResponse).toList();
    }

    public RoleConflictResponse create(Actor actor, CreateRoleConflictRequest request) {
        var roleIds = known(request.roleIds());
        var description = Objects.requireNonNullElse(request.description(), "").trim();
        var rule = conflicts.save(new RoleConflict(request.name().trim(), description, roleIds, clock.instant()));
        var changes = new ArrayList<FieldChange>();
        changes.add(FieldChange.set("name", rule.getName()));
        if (!description.isEmpty()) {
            changes.add(FieldChange.set("description", description));
        }
        changes.add(FieldChange.list("roles", strings(ordered(roleIds)), List.of()));
        events.publishEvent(new RoleConflictCreated(actor.userId(), rule.getId(), rule.getName(), List.copyOf(changes)));
        return toResponse(rule);
    }

    public RoleConflictResponse update(Actor actor, UUID id, UpdateRoleConflictRequest request) {
        var rule = conflicts.findById(id).orElseThrow(NOT_FOUND::exception);
        var name = request.name() == null ? rule.getName() : request.name().trim();
        var description = request.description() == null ? rule.getDescription() : request.description().trim();
        var roleIds = request.roleIds() == null ? rule.getRoleIds() : known(request.roleIds());
        var changes = new ArrayList<FieldChange>();
        FieldChange.diff("name", rule.getName(), name).ifPresent(changes::add);
        FieldChange.diff("description", rule.getDescription(), description).ifPresent(changes::add);
        FieldChange.listDiff("roles", strings(ordered(rule.getRoleIds())), strings(ordered(roleIds))).ifPresent(changes::add);
        rule.update(name, description, roleIds, clock.instant());
        if (!changes.isEmpty()) {
            events.publishEvent(new RoleConflictUpdated(actor.userId(), id, name, List.copyOf(changes)));
        }
        return toResponse(rule);
    }

    public void delete(Actor actor, UUID id) {
        var rule = conflicts.findById(id).orElseThrow(NOT_FOUND::exception);
        events.publishEvent(new RoleConflictDeleted(actor.userId(), id, rule.getName(),
                List.of(FieldChange.list("roles", List.of(), strings(ordered(rule.getRoleIds()))))));
        conflicts.delete(rule);
    }

    /** A deleted role comes off the rules; a rule left with fewer than two goes (P-022) */
    void forgetRole(UUID roleId) {
        for (var rule : conflicts.findWithRole(roleId)) {
            if (!rule.forget(roleId, clock.instant())) {
                conflicts.delete(rule);
            }
        }
        conflicts.flush();
    }

    /** Different roles that exist: duplicates are invalid_request, an unknown one not_found */
    private Set<UUID> known(List<UUID> roleIds) {
        var distinct = new HashSet<>(roleIds);
        if (distinct.size() != roleIds.size()) {
            throw INVALID_REQUEST.exception();
        }
        if (roles.findAllById(distinct).size() != distinct.size()) {
            throw NOT_FOUND.exception();
        }
        return distinct;
    }

    private static RoleConflictResponse toResponse(RoleConflict rule) {
        return new RoleConflictResponse(rule.getId(), rule.getName(), rule.getDescription(), ordered(rule.getRoleIds()));
    }

    /** Oldest role first: ids are UUID v7 */
    private static List<UUID> ordered(Set<UUID> roleIds) {
        return roleIds.stream().sorted().toList();
    }

    private static List<String> strings(List<UUID> ids) {
        return ids.stream().map(UUID::toString).toList();
    }
}
