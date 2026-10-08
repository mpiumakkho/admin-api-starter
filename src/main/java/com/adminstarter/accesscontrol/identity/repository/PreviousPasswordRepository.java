package com.adminstarter.accesscontrol.identity.repository;

import com.adminstarter.accesscontrol.identity.entity.PreviousPassword;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreviousPasswordRepository extends JpaRepository<PreviousPassword, UUID> {

    /** Newest first; ids are UUID v7, so they break ties in the order the passwords were replaced */
    List<PreviousPassword> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);
}
