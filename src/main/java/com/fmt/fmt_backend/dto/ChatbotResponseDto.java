package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ChatbotResponseDto {
    private UUID id;
    private String state;
    private String triggerType;
    private String triggerValue;
    private String messageType;
    private String responseText;
    private String buttonsJson;
    private String nextState;
    private String updatesLeadStatus;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
