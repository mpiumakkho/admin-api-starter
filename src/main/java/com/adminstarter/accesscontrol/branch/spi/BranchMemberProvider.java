package com.adminstarter.accesscontrol.branch.spi;

import java.util.UUID;

/**
 * Whether anyone belongs to a branch or holds a role there now or later, so a branch in use is not deleted
 * (branch_in_use). Identity, which keeps the people and their role assignments, answers it; branch depends only on this
 * interface, not on identity (P-020).
 */
public interface BranchMemberProvider {

    boolean inUse(UUID branchId);
}
