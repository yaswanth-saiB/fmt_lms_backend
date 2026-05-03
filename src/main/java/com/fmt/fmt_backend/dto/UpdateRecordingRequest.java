package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateRecordingRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private Integer durationMins;
}
