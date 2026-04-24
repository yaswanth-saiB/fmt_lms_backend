package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ReviewRequest {

    @NotBlank(message = "Reviewer name is required")
    @Size(max = 100, message = "Reviewer name must be 100 characters or less")
    private String reviewerName;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @NotBlank(message = "Review text is required")
    private String reviewText;

    // e.g. "April 2024" or "2 months ago" — optional
    private String reviewDate;

    // Paste Google profile photo URL — optional
    @Size(max = 500, message = "Photo URL must be 500 characters or less")
    private String photoUrl;

    // Null = keep existing; set to false to deactivate (PUT only)
    private Boolean isActive;

    private Integer displayOrder;
}
