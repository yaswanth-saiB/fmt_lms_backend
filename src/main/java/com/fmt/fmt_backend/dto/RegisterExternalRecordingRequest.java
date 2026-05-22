package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RegisterExternalRecordingRequest {

    @NotEmpty(message = "At least one batch ID is required")
    private List<UUID> batchIds;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Bunny video ID is required")
    private String bunnyVideoId;

    private Integer durationMins;
}
