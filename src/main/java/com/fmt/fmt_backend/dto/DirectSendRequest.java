package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class DirectSendRequest {

    @NotBlank
    private String phone;

    private java.util.UUID leadId; // optional — links conversation to lead when provided

    /** TEXT or TEMPLATE */
    @NotBlank
    private String type;

    /** For TEXT messages */
    private String message;

    /** For TEMPLATE messages */
    private String templateName;
    private List<String> params;
    private List<String> paramNames; // variable names parallel to params; null entry = positional
    private String headerImageHandle; // for IMAGE-header templates
}
