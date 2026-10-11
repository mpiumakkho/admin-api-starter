package com.adminstarter.accesscontrol.authorization.repository;

import com.adminstarter.accesscontrol.authorization.entity.RoleConflict;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleConflictRepository extends JpaRepository<RoleConflict, UUID> {

    /** Oldest first: ids are UUID v7, so they sort by creation */
    List<RoleConflict> findAllByOrderByIdAsc();

    /** The rules that name this role */
    @Query("select distinct c from RoleConflict c join c.roleIds r where r = :roleId")
    List<RoleConflict> findWithRole(UUID roleId);
}
