package com.adminstarter.accesscontrol.authentication.repository;

import com.adminstarter.accesscontrol.authentication.entity.UserSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** Not ended and not run out, most recently used first */
    @Query("""
            select s from UserSession s
            where s.userId = :userId and s.endedAt is null and s.expiresAt > :now
            order by s.lastSeenAt desc""")
    List<UserSession> findOpen(UUID userId, Instant now);

    /** Ended or run out before {@code before}; their refresh tokens go with them (on delete cascade) */
    @Modifying
    @Query("delete from UserSession s where s.endedAt < :before or s.expiresAt < :before")
    int deleteClosedBefore(Instant before);
}
