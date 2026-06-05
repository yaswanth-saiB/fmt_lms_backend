package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappTemplateConfigRequest {

    @NotBlank(message = "templateName is required")
    private String templateName;

    private String description;
    private String headerMediaId;
    /** IMAGE / VIDEO / DOCUMENT — must match the template's header format in Meta. */
    private String headerMediaType;
    private String headerLabel;
    private List<TemplateButtonDto> buttons;
    private Boolean isActive;
}
