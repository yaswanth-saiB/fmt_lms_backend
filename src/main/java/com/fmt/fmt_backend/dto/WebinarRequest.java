package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WebinarRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Scheduled time is required")
    private LocalDateTime scheduledAt;

    private String zoomLink;

    private UUID hostMentorId;

    private Integer maxCapacity;
}
