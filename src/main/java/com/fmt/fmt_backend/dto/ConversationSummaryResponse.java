package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.ConversationEntryPoint;
import com.fmt.fmt_backend.enums.ConversationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ConversationSummaryResponse {
    private UUID id;
    private UUID leadId;
    private String leadName;
    private String phone;
    private ConversationStatus status;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Integer unreadCount;
    private LocalDateTime windowExpiresAt;
    private Long minutesLeftInWindow;
    private String assignedToName;
    private ConversationEntryPoint entryPoint;
    private Boolean chatbotActive;
    private String labels;
}
