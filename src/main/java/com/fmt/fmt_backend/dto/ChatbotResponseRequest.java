package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatbotResponseRequest {
    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Trigger type is required (BUTTON_ID, KEYWORD, DEFAULT)")
    private String triggerType;

    private String triggerValue;

    @NotBlank(message = "Message type is required (TEXT, INTERACTIVE_BUTTONS, TEMPLATE)")
    private String messageType;

    private String responseText;
    private String buttonsJson;
    private String nextState;
    private String updatesLeadStatus;
    private Boolean isActive;
}
