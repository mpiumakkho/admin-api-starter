package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.branch.spi.BranchMemberProvider;
import com.adminstarter.accesscontrol.identity.repository.RoleAssignmentRepository;
import com.adminstarter.accesscontrol.identity.repository.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tells branch whether anyone is in a branch, without branch depending on identity (P-020) */
@Service
@Transactional(readOnly = true)
public class BranchMemberResolver implements BranchMemberProvider {

    private final UserRepository users;
    private final RoleAssignmentRepository assignments;
    private final Clock clock;

    public BranchMemberResolver(UserRepository users, RoleAssignmentRepository assignments, Clock clock) {
        this.users = users;
        this.assignments = assignments;
        this.clock = clock;
    }

    /** Someone belongs to it, or holds a role limited to it now or later */
    @Override
    public boolean inUse(UUID branchId) {
        return users.existsByBranchId(branchId) || assignments.heldNowOrLaterIn(branchId, clock.instant());
    }
}
