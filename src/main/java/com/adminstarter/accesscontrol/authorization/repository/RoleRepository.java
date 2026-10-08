package com.adminstarter.accesscontrol.authorization.repository;

import com.adminstarter.accesscontrol.authorization.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    /** Oldest first: ids are UUID v7, so they sort by creation */
    List<Role> findAllByOrderByIdAsc();

    Optional<Role> findByCode(String code);
}
