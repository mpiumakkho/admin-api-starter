package com.adminstarter.accesscontrol.review.repository;

import com.adminstarter.accesscontrol.review.entity.AccessReviewItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccessReviewItemRepository extends JpaRepository<AccessReviewItem, UUID> {

    /** By person, then role, as the review page lists them */
    List<AccessReviewItem> findByReviewIdOrderByUserNameAscRoleNameAscIdAsc(UUID reviewId);

    boolean existsByReviewIdAndDecidedBy(UUID reviewId, UUID decidedBy);

    /** How many roles each review covers, and how many have a decision */
    @Query("select i.reviewId as reviewId, count(i) as total, count(i.decision) as decided from AccessReviewItem i group by i.reviewId")
    List<Counts> countByReview();

    interface Counts {

        UUID getReviewId();

        long getTotal();

        long getDecided();
    }
}
