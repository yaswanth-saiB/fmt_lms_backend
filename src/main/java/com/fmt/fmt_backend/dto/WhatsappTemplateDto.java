package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsappTemplateDto {
    private String name;
    private String status;
    private String category;
    private String language;
    private String bodyText;
    private int paramCount;
}
