package com.adminstarter.accesscontrol.branch.repository;

import com.adminstarter.accesscontrol.branch.entity.Branch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    /** By name, as the Branches page lists them */
    List<Branch> findAllByOrderByNameAscIdAsc();

    /** Codes are kept upper-case, so this ignores case */
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
