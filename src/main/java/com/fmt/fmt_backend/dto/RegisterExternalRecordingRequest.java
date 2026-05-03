package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterExternalRecordingRequest {

    @NotNull(message = "Batch ID is required")
    private UUID batchId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Bunny video ID is required")
    private String bunnyVideoId;

    private Integer durationMins;
}
