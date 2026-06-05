package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmt.fmt_backend.enums.ButtonActionType;
import lombok.*;

import java.util.Map;

/**
 * One row inside the WhatsappTemplateConfig.buttonsJson array.
 * Maps a button payload (the text WhatsApp sends back when user clicks) to a chatbot action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateButtonDto {

    /** The button text WhatsApp sends back on click (matches the template button label). */
    private String payload;

    /** Which action to execute. */
    private ButtonActionType actionType;

    /** Free-form params for the action — e.g. { "text": "..." } for SEND_TEXT. */
    private Map<String, Object> actionParams;

    /** Admin-visible note explaining what this button does. */
    private String description;
}
