package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {
    private UUID id;
    private String reviewerName;
    private Integer rating;
    private String reviewText;
    private String reviewDate;
    private String photoUrl;
    private boolean isActive;
    private Integer displayOrder;
    private LocalDateTime createdAt;
}
