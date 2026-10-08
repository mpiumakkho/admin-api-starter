package com.adminstarter.accesscontrol.identity.repository;

import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    /** Who has two-step sign-in on, for listing users without a query per user */
    @Query("select c.userId from UserCredential c where c.twoFactorSecret is not null")
    Set<UUID> findTwoFactorUserIds();
}
