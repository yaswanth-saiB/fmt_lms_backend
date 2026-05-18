package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class MeetingRequest {

    @NotEmpty(message = "At least one batch ID is required")
    private List<UUID> batchIds;

    @NotBlank(message = "Topic is required")
    private String topic;

    private Integer durationMins;

    private LocalDateTime scheduledAt;

    /** Admin only — which mentor is conducting this class. Ignored when called from mentor API (JWT is used). */
    private UUID mentorId;
}
