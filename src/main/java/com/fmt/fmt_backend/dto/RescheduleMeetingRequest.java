package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RescheduleMeetingRequest {

    @NotNull(message = "New scheduled time is required")
    private LocalDateTime scheduledAt;

    private Integer durationMins;

    private String topic;
}
