package com.adminstarter.accesscontrol.branch.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.BRANCH_IN_USE;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.CODE_TAKEN;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.branch.dto.BranchResponse;
import com.adminstarter.accesscontrol.branch.dto.CreateBranchRequest;
import com.adminstarter.accesscontrol.branch.dto.UpdateBranchRequest;
import com.adminstarter.accesscontrol.branch.entity.Branch;
import com.adminstarter.accesscontrol.branch.event.BranchCreated;
import com.adminstarter.accesscontrol.branch.event.BranchDeleted;
import com.adminstarter.accesscontrol.branch.event.BranchUpdated;
import com.adminstarter.accesscontrol.branch.repository.BranchRepository;
import com.adminstarter.accesscontrol.branch.spi.BranchMemberProvider;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.dto.FieldChange;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adding, changing, closing and deleting branches (branches.manage), with the rules in API.md, "Branches" */
@Service
@Transactional
public class BranchManagementService {

    private final BranchRepository branches;
    private final BranchMemberProvider members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BranchManagementService(BranchRepository branches, BranchMemberProvider members,
            ApplicationEventPublisher events, Clock clock) {
        this.branches = branches;
        this.members = members;
        this.events = events;
        this.clock = clock;
    }

    /** Everyone signed in reads branches, to show branch names */
    @Transactional(readOnly = true)
    public List<BranchResponse> list() {
        return branches.findAllByOrderByNameAscIdAsc().stream().map(BranchManagementService::toResponse).toList();
    }

    public BranchResponse create(Actor actor, CreateBranchRequest request) {
        var code = code(request.code());
        if (branches.existsByCode(code)) {
            throw CODE_TAKEN.exception();
        }
        var branch = branches.save(new Branch(code, request.name().trim(), clock.instant()));
        events.publishEvent(new BranchCreated(actor.userId(), branch.getId(), branch.getName(), List.of(
                FieldChange.set("code", branch.getCode()),
                FieldChange.set("name", branch.getName()))));
        return toResponse(branch);
    }

    public BranchResponse update(Actor actor, UUID id, UpdateBranchRequest request) {
        var branch = branches.findById(id).orElseThrow(NOT_FOUND::exception);
        var code = request.code() == null ? branch.getCode() : code(request.code());
        var name = request.name() == null ? branch.getName() : request.name().trim();
        var active = Objects.requireNonNullElse(request.active(), branch.isActive());
        if (!code.equals(branch.getCode()) && branches.existsByCodeAndIdNot(code, id)) {
            throw CODE_TAKEN.exception();
        }
        // In the order of Branch in models.ts
        var changes = new ArrayList<FieldChange>();
        FieldChange.diff("code", branch.getCode(), code).ifPresent(changes::add);
        FieldChange.diff("name", branch.getName(), name).ifPresent(changes::add);
        FieldChange.diff("active", branch.isActive(), active).ifPresent(changes::add);
        branch.update(code, name, active, clock.instant());
        if (!changes.isEmpty()) {
            events.publishEvent(new BranchUpdated(actor.userId(), id, name, List.copyOf(changes)));
        }
        return toResponse(branch);
    }

    /** Its ended role assignments go with it (on delete cascade) */
    public void delete(Actor actor, UUID id) {
        var branch = branches.findById(id).orElseThrow(NOT_FOUND::exception);
        if (members.inUse(id)) {
            throw BRANCH_IN_USE.exception();
        }
        events.publishEvent(new BranchDeleted(actor.userId(), id, branch.getName(),
                List.of(FieldChange.was("code", branch.getCode()))));
        branches.delete(branch);
        // Someone may have joined it meanwhile: fk_users_branch refuses the delete, and ApiExceptionHandler answers
        // branch_in_use. Flushing here makes that happen inside this transaction.
        branches.flush();
    }

    /** Trimmed and upper-case, as codes are kept */
    private static String code(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static BranchResponse toResponse(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getCode(), branch.getName(), branch.isActive(), branch.getCreatedAt());
    }
}
