package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmt.fmt_backend.entity.LeadPayment;
import com.fmt.fmt_backend.enums.PaymentStatus;
import com.fmt.fmt_backend.enums.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadPaymentResponse {

    private UUID id;
    private BigDecimal amount;
    private PaymentType paymentType;
    private LocalDate dueDate;
    private LocalDateTime paidAt;
    private PaymentStatus status;
    private String notes;
    private String recordedByName;
    private LocalDateTime createdAt;

    public static LeadPaymentResponse from(LeadPayment p) {
        String recordedByName = null;
        if (p.getRecordedBy() != null) {
            recordedByName = p.getRecordedBy().getFirstName() + " " + p.getRecordedBy().getLastName();
        }
        return LeadPaymentResponse.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .paymentType(p.getPaymentType())
                .dueDate(p.getDueDate())
                .paidAt(p.getPaidAt())
                .status(p.getStatus())
                .notes(p.getNotes())
                .recordedByName(recordedByName)
                .createdAt(p.getCreatedAt())
                .build();
    }
}
