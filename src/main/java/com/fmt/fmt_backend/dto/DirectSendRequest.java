package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class DirectSendRequest {

    @NotBlank
    private String phone;

    /** TEXT or TEMPLATE */
    @NotBlank
    private String type;

    /** For TEXT messages */
    private String message;

    /** For TEMPLATE messages */
    private String templateName;
    private List<String> params;
}
