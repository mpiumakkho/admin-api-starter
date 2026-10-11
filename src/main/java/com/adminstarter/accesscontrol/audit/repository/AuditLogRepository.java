package com.adminstarter.accesscontrol.audit.repository;

import com.adminstarter.accesscontrol.audit.entity.AuditLog;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Newest first, at or after {@code fromTime} and before {@code toTime}; a null leaves that end open. Ids are
     * UUID v7, so they break ties in the order entries were written. The casts tell PostgreSQL the type of a null
     * parameter, which it cannot work out from {@code ? is null} alone.
     */
    @Query("""
            select a from AuditLog a
            where (cast(:fromTime as Instant) is null or a.occurredAt >= :fromTime)
              and (cast(:toTime as Instant) is null or a.occurredAt < :toTime)
            order by a.occurredAt desc, a.id desc""")
    List<AuditLog> findBetween(@Nullable Instant fromTime, @Nullable Instant toTime, Limit limit);

    /** As findBetween, only the entries about these people: their ids as text, as target_id keeps them (P-024) */
    @Query("""
            select a from AuditLog a
            where a.targetId in :targetIds
              and (cast(:fromTime as Instant) is null or a.occurredAt >= :fromTime)
              and (cast(:toTime as Instant) is null or a.occurredAt < :toTime)
            order by a.occurredAt desc, a.id desc""")
    List<AuditLog> findBetweenAbout(Collection<String> targetIds, @Nullable Instant fromTime, @Nullable Instant toTime,
            Limit limit);

    @Modifying
    @Query("delete from AuditLog a where a.occurredAt < :before")
    int deleteOlderThan(Instant before);
}
