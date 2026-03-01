package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateBatchRequest {

    @NotBlank(message = "Batch name is required")
    private String name;

    @NotNull(message = "Course ID is required")
    private UUID courseId;

    @NotNull(message = "Start date is required")
    @Future(message = "Start date must be in future")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Future(message = "End date must be in future")
    private LocalDate endDate;

    @Min(value = 1, message = "Max students must be at least 1")
    private Integer maxStudents = 30;
}