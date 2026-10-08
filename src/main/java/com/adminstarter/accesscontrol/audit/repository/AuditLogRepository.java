package com.adminstarter.accesscontrol.audit.repository;

import com.adminstarter.accesscontrol.audit.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Newest first; ids are UUID v7, so they break ties in the order entries were written */
    List<AuditLog> findAllByOrderByOccurredAtDescIdDesc(Limit limit);

    @Modifying
    @Query("delete from AuditLog a where a.occurredAt < :before")
    int deleteOlderThan(Instant before);
}
