package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WhatsappTemplateDto {
    private String name;
    private String status;
    private String category;
    private String language;
    private String bodyText;
    private int paramCount;
    /** Variable names in body order — null entry means positional ({{1}}) and needs no parameter_name field */
    private List<String> paramNames;
    /** "IMAGE", "VIDEO", "DOCUMENT", "TEXT", or null if no header */
    private String headerType;
    /** Media handle from template example — reusable as headerImageId when sending IMAGE header templates */
    private String headerImageHandle;
    /** Button labels in order, extracted from Meta's BUTTONS component.
     *  Used by /admin/whatsapp-templates UI to populate the button-payload dropdown. */
    private List<String> buttonTexts;
}
