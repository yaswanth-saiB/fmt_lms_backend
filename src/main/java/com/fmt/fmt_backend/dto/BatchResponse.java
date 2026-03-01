package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class BatchResponse {
    private UUID id;
    private String name;
    private UUID courseId;
    private String courseName;
    private UUID mentorId;
    private String mentorName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxStudents;
    private Integer currentStudents;
    private String status;
    private Integer meetingCount;
}