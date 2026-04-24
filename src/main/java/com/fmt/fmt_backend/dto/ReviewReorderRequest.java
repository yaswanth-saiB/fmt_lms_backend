package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ReviewReorderRequest {

    @NotNull(message = "Review ID is required")
    private UUID id;

    @NotNull(message = "Display order is required")
    private Integer displayOrder;
}
