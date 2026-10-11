package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.authorization.spi.RoleHolderProvider;
import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.dto.RoleAssignmentResponse;
import com.adminstarter.accesscontrol.identity.entity.RoleAssignment;
import com.adminstarter.accesscontrol.identity.repository.RoleAssignmentRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who holds which role, and when (P-019): what the user responses, the per-request check and the rules read. It also
 * tells authorization whether a role is in use, without authorization depending on identity.
 */
@Service
@Transactional(readOnly = true)
public class RoleAssignmentResolver implements RoleHolderProvider {

    private final RoleAssignmentRepository assignments;
    private final RoleService roles;
    private final Clock clock;

    public RoleAssignmentResolver(RoleAssignmentRepository assignments, RoleService roles, Clock clock) {
        this.assignments = assignments;
        this.roles = roles;
        this.clock = clock;
    }

    /** A person's assignments, the ones that count now and scheduled and ended ones, oldest first */
    public List<RoleAssignment> of(UUID userId) {
        return assignments.findByUserIdOrderByStartsAtAscIdAsc(userId);
    }

    /** Everyone's, by person, for the users list */
    public Map<UUID, List<RoleAssignment>> byUser() {
        return assignments.findAllByOrderByStartsAtAscIdAsc().stream()
                .collect(Collectors.groupingBy(RoleAssignment::getUserId));
    }

    /**
     * The assignments someone holds now or will hold: whoever manages them must be able to give each of these, in its
     * scope (P-024)
     */
    public List<RoleAssignment> heldBy(UUID userId) {
        var now = clock.instant();
        return of(userId).stream().filter(item -> item.heldAt(now)).toList();
    }

    /** Whether this person holds a role with every permission, now, for good and for the whole organization */
    public boolean isAdministrator(UUID userId) {
        var full = roles.fullAccessRoleIds();
        return !full.isEmpty() && assignments.isPermanentHolder(userId, full, clock.instant());
    }

    /** How many active people hold a role with every permission, now and for good; at least one must stay */
    public long administrators() {
        var full = roles.fullAccessRoleIds();
        return full.isEmpty() ? 0 : assignments.countPermanentHolders(full, clock.instant());
    }

    @Override
    public boolean heldNowOrLater(UUID roleId) {
        return assignments.heldNowOrLater(roleId, clock.instant());
    }

    static RoleAssignmentResponse toResponse(RoleAssignment item) {
        return new RoleAssignmentResponse(item.getId(), item.getUserId(), item.getRoleId(), item.getBranchId(),
                item.getStartsAt(), item.getEndsAt(), item.getReason(), item.getAssignedBy(), item.getAssignedAt());
    }

    static AccountSummary.Grant toGrant(RoleAssignment item) {
        return new AccountSummary.Grant(item.getRoleId(), item.getBranchId(), item.getStartsAt(), item.getEndsAt());
    }
}
