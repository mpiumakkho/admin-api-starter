package com.adminstarter.accesscontrol.identity.repository;

import com.adminstarter.accesscontrol.identity.entity.RecoveryCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    /** Uses a code up, once: 1, or 0 when another request used it first (P-036) */
    @Modifying
    @Query("update RecoveryCode c set c.usedAt = :now where c.id = :id and c.usedAt is null")
    int useUp(UUID id, Instant now);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteByUserId(UUID userId);
}
