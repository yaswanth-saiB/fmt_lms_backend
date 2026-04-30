package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class LeadAssignRequest {

    @NotNull(message = "assignedTo (userId) is required")
    private UUID assignedTo;
}
