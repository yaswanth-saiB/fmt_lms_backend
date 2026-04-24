package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.ReviewRequest;
import com.fmt.fmt_backend.dto.ReviewReorderRequest;
import com.fmt.fmt_backend.dto.ReviewResponse;
import com.fmt.fmt_backend.entity.Review;
import com.fmt.fmt_backend.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;

    // =========================================================================
    // PUBLIC — cached, active reviews only
    // =========================================================================

    /**
     * Returns active reviews ordered by display_order ASC.
     * Cached for 1 hour — evicted on any admin write operation.
     * Called by the public homepage with no auth.
     */
    @Cacheable("activeReviews")
    public List<ReviewResponse> getActiveReviews() {
        List<ReviewResponse> reviews = reviewRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
        log.info("Loaded {} active review(s) from DB (cache miss)", reviews.size());
        return reviews;
    }

    // =========================================================================
    // ADMIN — full management, no caching
    // =========================================================================

    /** All reviews including inactive — for admin panel. */
    public List<ReviewResponse> getAllReviews() {
        return reviewRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Single review by ID — for admin edit form. */
    public ReviewResponse getReview(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    @CacheEvict(value = "activeReviews", allEntries = true)
    public ReviewResponse createReview(ReviewRequest request) {
        Review review = Review.builder()
                .reviewerName(request.getReviewerName())
                .rating(request.getRating())
                .reviewText(request.getReviewText())
                .reviewDate(request.getReviewDate())
                .photoUrl(request.getPhotoUrl())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Review created: id={}, reviewer={}", saved.getId(), saved.getReviewerName());
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "activeReviews", allEntries = true)
    public ReviewResponse updateReview(UUID id, ReviewRequest request) {
        Review review = findOrThrow(id);

        review.setReviewerName(request.getReviewerName());
        review.setRating(request.getRating());
        review.setReviewText(request.getReviewText());
        review.setReviewDate(request.getReviewDate());
        review.setPhotoUrl(request.getPhotoUrl());
        if (request.getIsActive() != null) {
            review.setActive(request.getIsActive());
        }
        if (request.getDisplayOrder() != null) {
            review.setDisplayOrder(request.getDisplayOrder());
        }

        Review saved = reviewRepository.save(review);
        log.info("Review updated: id={}, reviewer={}, active={}",
                saved.getId(), saved.getReviewerName(), saved.isActive());
        return toResponse(saved);
    }

    /** Soft delete — sets is_active = false. Use PUT with isActive=true to restore. */
    @Transactional
    @CacheEvict(value = "activeReviews", allEntries = true)
    public void deleteReview(UUID id) {
        Review review = findOrThrow(id);
        review.setActive(false);
        reviewRepository.save(review);
        log.info("Review soft-deleted: id={}, reviewer={}", id, review.getReviewerName());
    }

    /**
     * Batch-updates display_order for multiple reviews at once.
     * Frontend sends the full ordered list after drag-and-drop reorder.
     */
    @Transactional
    @CacheEvict(value = "activeReviews", allEntries = true)
    public void reorderReviews(List<ReviewReorderRequest> items) {
        for (ReviewReorderRequest item : items) {
            Review review = findOrThrow(item.getId());
            review.setDisplayOrder(item.getDisplayOrder());
            reviewRepository.save(review);
        }
        log.info("Reordered {} review(s)", items.size());
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private Review findOrThrow(UUID id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Review not found"));
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .reviewerName(r.getReviewerName())
                .rating(r.getRating())
                .reviewText(r.getReviewText())
                .reviewDate(r.getReviewDate())
                .photoUrl(r.getPhotoUrl())
                .isActive(r.isActive())
                .displayOrder(r.getDisplayOrder())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
