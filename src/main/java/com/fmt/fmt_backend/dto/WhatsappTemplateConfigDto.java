package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WhatsappTemplateConfigDto {
    private UUID id;
    private String templateName;
    private String description;
    private String headerMediaId;
    private String headerMediaType;
    private String headerLabel;
    private List<TemplateButtonDto> buttons;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
