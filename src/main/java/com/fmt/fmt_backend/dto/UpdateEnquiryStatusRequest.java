package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.entity.Enquiry;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEnquiryStatusRequest {

    @NotNull(message = "Status is required")
    private Enquiry.EnquiryStatus status;
}
