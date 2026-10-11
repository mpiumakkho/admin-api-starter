package com.adminstarter.accesscontrol.branch.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.BRANCH_CLOSED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.NOT_FOUND;

import com.adminstarter.accesscontrol.branch.repository.BranchRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branches;

    public BranchServiceImpl(BranchRepository branches) {
        this.branches = branches;
    }

    @Override
    public void requireOpen(UUID branchId) {
        if (!branches.findById(branchId).orElseThrow(NOT_FOUND::exception).isActive()) {
            throw BRANCH_CLOSED.exception();
        }
    }
}
