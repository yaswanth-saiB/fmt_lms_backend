package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MeetingRequest {

    @NotNull(message = "Batch ID is required")
    private UUID batchId;

    @NotBlank(message = "Topic is required")
    private String topic;

    private Integer durationMins;

    private LocalDateTime scheduledAt;
}
