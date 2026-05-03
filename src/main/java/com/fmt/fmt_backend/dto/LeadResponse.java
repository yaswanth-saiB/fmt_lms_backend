package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.enums.ClosingBlocker;
import com.fmt.fmt_backend.enums.DemoType;
import com.fmt.fmt_backend.enums.LeadSource;
import com.fmt.fmt_backend.enums.LeadStatus;
import com.fmt.fmt_backend.enums.PreferredTiming;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadResponse {

    private UUID id;
    private String name;
    private String phone;
    private String alternatePhone;
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
    private String notes;
    private String assignedToName;
    private String currentLevel;
    private String preferredLearningMode;
    private PreferredTiming preferredTimings;
    private LocalDateTime sheetCreatedAt;
    private Integer leadAgeDays;

    // Demo
    private String demoMentorName;
    private LocalDateTime demoScheduledAt;
    private LocalDateTime demoConductedAt;
    private DemoType demoType;

    // Closing
    private ClosingBlocker closingBlocker;
    private String closingComment;

    // Payment
    private BigDecimal courseFee;
    private BigDecimal totalPaid;
    private BigDecimal balance;
    private String closedByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LeadActivityResponse> activities;
    private List<LeadPaymentResponse> payments;

    public static LeadResponse from(Lead lead, List<LeadActivityResponse> activities,
                                    List<LeadPaymentResponse> payments, BigDecimal totalPaid) {
        String assignedToName = null;
        if (lead.getAssignedTo() != null) {
            assignedToName = lead.getAssignedTo().getFirstName() + " " + lead.getAssignedTo().getLastName();
        }

        String demoMentorName = null;
        if (lead.getDemoMentor() != null) {
            demoMentorName = lead.getDemoMentor().getFirstName() + " " + lead.getDemoMentor().getLastName();
        }

        String closedByName = null;
        if (lead.getClosedBy() != null) {
            closedByName = lead.getClosedBy().getFirstName() + " " + lead.getClosedBy().getLastName();
        }

        Integer daysSinceLastCall = null;
        if (lead.getLastCallAt() != null) {
            daysSinceLastCall = (int) ChronoUnit.DAYS.between(
                    lead.getLastCallAt().toLocalDate(), LocalDate.now());
        }

        LocalDateTime ref = lead.getSheetCreatedAt() != null ? lead.getSheetCreatedAt() : lead.getCreatedAt();
        int leadAgeDays = ref != null ? (int) ChronoUnit.DAYS.between(ref.toLocalDate(), LocalDate.now()) : 0;

        BigDecimal balance = null;
        if (lead.getCourseFee() != null && totalPaid != null) {
            balance = lead.getCourseFee().subtract(totalPaid);
        }

        return LeadResponse.builder()
                .id(lead.getId())
                .name(lead.getName())
                .phone(lead.getPhone())
                .alternatePhone(lead.getAlternatePhone())
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
                .notes(lead.getNotes())
                .assignedToName(assignedToName)
                .currentLevel(lead.getCurrentLevel())
                .preferredLearningMode(lead.getPreferredLearningMode())
                .preferredTimings(lead.getPreferredTimings())
                .sheetCreatedAt(lead.getSheetCreatedAt())
                .leadAgeDays(leadAgeDays)
                .demoMentorName(demoMentorName)
                .demoScheduledAt(lead.getDemoScheduledAt())
                .demoConductedAt(lead.getDemoConductedAt())
                .demoType(lead.getDemoType())
                .closingBlocker(lead.getClosingBlocker())
                .closingComment(lead.getClosingComment())
                .courseFee(lead.getCourseFee())
                .totalPaid(totalPaid)
                .balance(balance)
                .closedByName(closedByName)
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .activities(activities)
                .payments(payments)
                .build();
    }
}
