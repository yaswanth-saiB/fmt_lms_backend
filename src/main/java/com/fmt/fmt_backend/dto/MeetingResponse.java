package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MeetingResponse {
    private UUID id;
    private String topic;
    private String description;
    private UUID batchId;
    private String batchName;
    private String mentorName;
    private LocalDateTime startTime;
    private Integer durationMinutes;
    private String joinUrl;      // For students
    private String startUrl;      // For mentors (sensitive)
    private String status;
    private String recordingUrl;
}