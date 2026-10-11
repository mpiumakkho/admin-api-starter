package com.adminstarter.accesscontrol.branch.service;

import java.util.UUID;

/** What other modules may ask about branches */
public interface BranchService {

    /**
     * A branch someone can join or get a role in: it exists (not_found otherwise) and is open (branch_closed otherwise)
     */
    void requireOpen(UUID branchId);
}
