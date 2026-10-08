package com.adminstarter.accesscontrol.authorization.repository;

import com.adminstarter.accesscontrol.authorization.entity.Permission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findAllByOrderByPositionAsc();
}
