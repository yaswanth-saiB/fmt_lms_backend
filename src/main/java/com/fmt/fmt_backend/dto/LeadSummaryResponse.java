package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.enums.LeadSource;
import com.fmt.fmt_backend.enums.LeadStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadSummaryResponse {

    private UUID id;
    private String name;
    private String phone;
    private String email;
    private String courseInterest;
    private LeadSource source;
    private LeadStatus status;
    private Integer dnpCount;
    private Boolean whatsappEligible;
    private Boolean whatsappSent;
    private LocalDateTime followupDatetime;
    private LocalDateTime lastCallAt;
    private Integer daysSinceLastCall;
    private String assignedToName;
    private String currentLevel;
    private String preferredLearningMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LeadSummaryResponse from(Lead lead) {
        String assignedToName = null;
        if (lead.getAssignedTo() != null) {
            assignedToName = lead.getAssignedTo().getFirstName() + " " + lead.getAssignedTo().getLastName();
        }

        Integer daysSinceLastCall = null;
        if (lead.getLastCallAt() != null) {
            daysSinceLastCall = (int) ChronoUnit.DAYS.between(
                    lead.getLastCallAt().toLocalDate(), LocalDate.now());
        }

        return LeadSummaryResponse.builder()
                .id(lead.getId())
                .name(lead.getName())
                .phone(lead.getPhone())
                .email(lead.getEmail())
                .courseInterest(lead.getCourseInterest())
                .source(lead.getSource())
                .status(lead.getStatus())
                .dnpCount(lead.getDnpCount())
                .whatsappEligible(lead.getWhatsappEligible())
                .whatsappSent(lead.getWhatsappSent())
                .followupDatetime(lead.getFollowupDatetime())
                .lastCallAt(lead.getLastCallAt())
                .daysSinceLastCall(daysSinceLastCall)
                .assignedToName(assignedToName)
                .currentLevel(lead.getCurrentLevel())
                .preferredLearningMode(lead.getPreferredLearningMode())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }
}
