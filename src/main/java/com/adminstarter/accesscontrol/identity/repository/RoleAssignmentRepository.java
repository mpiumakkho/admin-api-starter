package com.adminstarter.accesscontrol.identity.repository;

import com.adminstarter.accesscontrol.identity.entity.RoleAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    /** Oldest first; ids are UUID v7, so they break ties in the order they were given */
    List<RoleAssignment> findByUserIdOrderByStartsAtAscIdAsc(UUID userId);

    List<RoleAssignment> findAllByOrderByStartsAtAscIdAsc();

    List<RoleAssignment> findByUserIdIn(Collection<UUID> userIds);

    /** Whether anyone holds the role now or will hold it later */
    @Query("select count(a) > 0 from RoleAssignment a where a.roleId = :roleId and (a.endsAt is null or a.endsAt > :now)")
    boolean heldNowOrLater(UUID roleId, Instant now);

    /**
     * How many active users hold, now, for good and for the whole organization, one of these roles: those with every
     * permission. At least one must stay (last_administrator).
     */
    @Query("""
            select count(distinct a.userId) from RoleAssignment a, User u
            where u.id = a.userId and u.status = com.adminstarter.accesscontrol.identity.entity.UserStatus.ACTIVE
              and a.roleId in :roleIds and a.branchId is null and a.endsAt is null and a.startsAt <= :now""")
    long countPermanentHolders(Collection<UUID> roleIds, Instant now);

    /** Whether this person holds, now, for good and for the whole organization, one of these roles */
    @Query("""
            select count(a) > 0 from RoleAssignment a
            where a.userId = :userId and a.roleId in :roleIds and a.branchId is null and a.endsAt is null and a.startsAt <= :now""")
    boolean isPermanentHolder(UUID userId, Collection<UUID> roleIds, Instant now);

    /** Whether anyone holds a role limited to the branch now or will hold one later */
    @Query("select count(a) > 0 from RoleAssignment a where a.branchId = :branchId and (a.endsAt is null or a.endsAt > :now)")
    boolean heldNowOrLaterIn(UUID branchId, Instant now);

    /** Ended, and not yet in the activity log */
    @Query("select a from RoleAssignment a where a.endsAt <= :now and a.expiryLoggedAt is null")
    List<RoleAssignment> findEndedUnlogged(Instant now);

    /** Marks the end as logged; 0 when another instance got there first */
    @Modifying
    @Query("update RoleAssignment a set a.expiryLoggedAt = :now where a.id = :id and a.expiryLoggedAt is null")
    int markExpiryLogged(UUID id, Instant now);

    /** Ended assignments older than the activity the settings keep */
    @Modifying
    @Query("delete from RoleAssignment a where a.endsAt < :before")
    int deleteEndedBefore(Instant before);
}
