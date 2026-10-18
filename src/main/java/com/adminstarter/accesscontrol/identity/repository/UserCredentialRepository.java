package com.adminstarter.accesscontrol.identity.repository;

import com.adminstarter.accesscontrol.identity.entity.UserCredential;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    /** Who has two-step sign-in on, for listing users without a query per user */
    @Query("select c.userId from UserCredential c where c.twoFactorSecret is not null")
    Set<UUID> findTwoFactorUserIds();

    /**
     * Takes a step of the codes from the app, once: 1 when it is later than the last one taken, 0 when it was taken
     * already, by this request or one at the same time (the database lets only one of them through; P-036)
     */
    @Modifying
    @Query("update UserCredential c set c.lastTotpStep = :step "
            + "where c.userId = :userId and (c.lastTotpStep is null or c.lastTotpStep < :step)")
    int useTotpStep(UUID userId, long step);
}
