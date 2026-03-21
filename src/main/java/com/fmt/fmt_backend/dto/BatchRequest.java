package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class BatchRequest {

    @NotBlank(message = "Batch name is required")
    private String name;

    @NotNull(message = "Course ID is required")
    private UUID courseId;

    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxStudents;
    private String description;
}
