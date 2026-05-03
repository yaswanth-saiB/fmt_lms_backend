package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.ClosingBlocker;
import com.fmt.fmt_backend.enums.DemoType;
import com.fmt.fmt_backend.enums.LeadStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UpdateLeadStatusRequest {

    @NotNull(message = "Status is required")
    private LeadStatus status;

    private String notes;
    private LocalDateTime followupDatetime;

    // Demo fields — provide when status = DEMO_BOOKED
    private UUID demoMentorId;
    private LocalDateTime demoScheduledAt;
    private DemoType demoType;

    // Demo done — provide when status = DEMO_DONE
    private LocalDateTime demoConductedAt;

    // Closing fields — provide when status = CLOSING
    private ClosingBlocker closingBlocker;
    private String closingComment;

    // Payment stage — provide when status = CLOSING or PAYMENT_DONE
    private BigDecimal courseFee;

    // WhatsApp response — provide when status = WHATSAPP_RESPONDED
    private String alternatePhone;
}
