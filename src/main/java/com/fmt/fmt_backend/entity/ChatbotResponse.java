package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "chatbot_responses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChatbotResponse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ChatbotState value — which state this response applies to. Use "*" for any state.
    @Column(name = "state", nullable = false, length = 50)
    private String state;

    // BUTTON_ID, KEYWORD, DEFAULT
    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    // The button_id or keyword that triggers this. Null for DEFAULT.
    @Column(name = "trigger_value", length = 100)
    private String triggerValue;

    // TEXT, INTERACTIVE_BUTTONS, TEMPLATE
    @Column(name = "message_type", nullable = false, length = 30)
    private String messageType;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    // JSON array of buttons: [{"id":"btn_id","title":"Button Title"}]
    @Column(name = "buttons_json", columnDefinition = "TEXT")
    private String buttonsJson;

    // State to transition to after sending this response
    @Column(name = "next_state", length = 50)
    private String nextState;

    // If set, updates the lead's status to this LeadStatus value
    @Column(name = "updates_lead_status", length = 30)
    private String updatesLeadStatus;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
