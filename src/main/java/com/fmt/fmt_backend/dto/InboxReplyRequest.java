package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class InboxReplyRequest {
    @NotBlank(message = "Message is required")
    private String message;
    private UUID replyToMessageId; // optional — for reply-to-specific-message
}
