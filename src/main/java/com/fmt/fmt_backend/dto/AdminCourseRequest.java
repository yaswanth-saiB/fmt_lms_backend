package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AdminCourseRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private BigDecimal price;

    @NotNull(message = "Mentor ID is required")
    private UUID mentorId;
}
