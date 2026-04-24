package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    // Public homepage — only active reviews, ordered by display_order
    List<Review> findByIsActiveTrueOrderByDisplayOrderAsc();

    // Admin panel — all reviews, latest first
    List<Review> findAllByOrderByDisplayOrderAscCreatedAtDesc();
}
