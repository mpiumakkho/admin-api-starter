package com.adminstarter.accesscontrol.review.controller;

import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.review.dto.AccessReviewDecisionRequest;
import com.adminstarter.accesscontrol.review.dto.AccessReviewItemResponse;
import com.adminstarter.accesscontrol.review.dto.AccessReviewResponse;
import com.adminstarter.accesscontrol.review.dto.StartAccessReviewRequest;
import com.adminstarter.accesscontrol.review.service.AccessReviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API.md, "Access reviews" (P-028): who sees and decides what is the service's to say, past reviews.manage */
@RestController
@RequestMapping("/api/access-reviews")
public class AccessReviewController {

    private final AccessReviewService reviews;

    public AccessReviewController(AccessReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AccessReviewResponse> list(@AuthenticationPrincipal Actor actor) {
        return reviews.list(actor);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('reviews.manage')")
    public AccessReviewResponse start(@AuthenticationPrincipal Actor actor, @Valid @RequestBody StartAccessReviewRequest request) {
        return reviews.start(actor, request);
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("isAuthenticated()")
    public List<AccessReviewItemResponse> items(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return reviews.items(actor, id);
    }

    @PostMapping("/{id}/decisions")
    @PreAuthorize("isAuthenticated()")
    public List<AccessReviewItemResponse> decide(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
            @Valid @RequestBody AccessReviewDecisionRequest request) {
        return reviews.decide(actor, id, request);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('reviews.manage')")
    public AccessReviewResponse close(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return reviews.close(actor, id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('reviews.manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        reviews.delete(actor, id);
    }
}
