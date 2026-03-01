package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CourseResponse {
    private UUID id;
    private String title;
    private String description;
    private UUID mentorId;
    private String mentorName;
    private BigDecimal price;
    private Integer durationHours;
    private String status;
    private Integer batchCount;
    private LocalDateTime createdAt;
}