package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.BatchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BatchResponse {
    private UUID id;
    private String name;
    private UUID courseId;
    private String courseName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxStudents;
    private long enrolledCount;
    private BatchStatus status;
    private String description;
    private LocalDateTime createdAt;
}
