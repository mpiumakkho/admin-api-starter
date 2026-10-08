package com.adminstarter.accesscontrol.authentication.repository;

import com.adminstarter.accesscontrol.authentication.entity.ActionToken;
import com.adminstarter.accesscontrol.authentication.entity.ActionTokenType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {

    Optional<ActionToken> findByTokenHash(String tokenHash);

    List<ActionToken> findByUserIdAndTypeAndUsedAtIsNull(UUID userId, ActionTokenType type);

    @Modifying
    @Query("delete from ActionToken t where t.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
