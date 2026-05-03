package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateBatchRequest {

    @NotBlank(message = "Batch name is required")
    private String name;

    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxStudents;
    private String description;
}
