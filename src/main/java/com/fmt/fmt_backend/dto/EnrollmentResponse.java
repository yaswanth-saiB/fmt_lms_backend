package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EnrollmentResponse {
    private UUID id;
    private UUID studentId;
    private String studentName;
    private UUID batchId;
    private String batchName;
    private String courseName;
    private LocalDateTime enrolledAt;
    private String status;
    private Integer progressPercentage;
}