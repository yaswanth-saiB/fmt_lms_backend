package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.LeadStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateLeadStatusRequest {

    @NotNull(message = "Status is required")
    private LeadStatus status;

    private String notes;
    private LocalDateTime followupDatetime;
}
