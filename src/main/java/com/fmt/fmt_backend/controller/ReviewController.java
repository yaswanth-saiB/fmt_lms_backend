package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.ReviewResponse;
import com.fmt.fmt_backend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Public reviews endpoint for the homepage")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    @Operation(summary = "Get all active reviews — public, no auth required, cached 1 hour",
               description = "Returns active reviews ordered by display_order ASC (lowest number first). " +
                             "Response is cached for 1 hour and evicted on any admin review change.")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getActiveReviews() {
        return ResponseEntity.ok(ApiResponse.success("Reviews fetched", reviewService.getActiveReviews()));
    }
}
