package com.adminstarter.accesscontrol.review.repository;

import com.adminstarter.accesscontrol.review.entity.AccessReview;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccessReviewRepository extends JpaRepository<AccessReview, UUID> {

    /** Newest first */
    List<AccessReview> findAllByOrderByCreatedAtDescIdDesc();

    /** Locked until the transaction ends, so a decision and closing it, or two instances' jobs, never cross */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccessReview r where r.id = :id")
    Optional<AccessReview> findForUpdate(UUID id);

    /** Open, and due by now: what AccessReviewJob closes; locked as findForUpdate */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccessReview r where r.status = com.adminstarter.accesscontrol.review.entity.AccessReviewStatus.OPEN"
            + " and r.dueAt <= :now")
    List<AccessReview> findDueForUpdate(Instant now);
}
