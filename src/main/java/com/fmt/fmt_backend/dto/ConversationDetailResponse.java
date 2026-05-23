package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.ConversationStatus;
import com.fmt.fmt_backend.enums.LeadStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ConversationDetailResponse {
    private UUID id;
    private ConversationStatus status;
    private Boolean chatbotActive;
    private String chatbotState;
    private LocalDateTime windowExpiresAt;
    private Long minutesLeftInWindow;
    private UUID assignedToId;
    private String assignedToName;
    private String labels;

    // Lead summary
    private UUID leadId;
    private String leadName;
    private String phone;
    private LeadStatus leadStatus;
    private String courseInterest;
    private String leadEmail;
    private String leadAssignedSalesName;
    private String leadCrmNotes;
    private LocalDateTime leadFollowupAt;
    private LocalDateTime leadLastCallAt;
    private BigDecimal leadCourseFee;

    private List<MessageResponse> messages;
    private List<NoteResponse> notes;
}
