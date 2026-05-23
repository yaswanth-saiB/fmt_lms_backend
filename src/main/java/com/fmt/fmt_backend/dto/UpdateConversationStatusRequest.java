package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.ConversationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateConversationStatusRequest {
    @NotNull(message = "Status is required")
    private ConversationStatus status;
}
