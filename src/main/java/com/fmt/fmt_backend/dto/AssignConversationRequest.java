package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignConversationRequest {
    @NotNull(message = "assignedTo user ID is required")
    private UUID assignedTo;
}
