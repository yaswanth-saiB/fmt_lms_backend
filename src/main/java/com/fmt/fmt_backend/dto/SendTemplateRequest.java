package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SendTemplateRequest {
    @NotBlank(message = "Template name is required")
    private String templateName;
    private List<String> parameters;
    private List<String> paramNames; // variable names parallel to parameters; null entry = positional
}
