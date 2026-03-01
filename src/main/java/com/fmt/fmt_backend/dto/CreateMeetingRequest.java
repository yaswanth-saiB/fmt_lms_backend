package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateMeetingRequest {

    @NotBlank(message = "Topic is required")
    private String topic;

    private String description;

    @NotNull(message = "Batch ID is required")
    private UUID batchId;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in future")
    private LocalDateTime startTime;

    @NotNull(message = "Duration is required")
    @Min(value = 15, message = "Duration must be at least 15 minutes")
    private Integer durationMinutes;
}
