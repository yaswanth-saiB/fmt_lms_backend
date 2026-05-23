package com.fmt.fmt_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmt.fmt_backend.entity.*;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotEngine {

    private final ChatbotResponseRepository chatbotResponseRepository;
    private final ChatbotSessionRepository chatbotSessionRepository;
    private final WhatsappMessageRepository messageRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final WhatsAppApiService whatsAppApiService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processMessage(WhatsappConversation conversation, String content, String buttonId, String buttonTitle) {
        String currentState = conversation.getChatbotState().name();

        // Determine intent — button takes priority over keyword
        String intent = resolveIntent(buttonId, content);

        // Find matching response: try specific state first, then wildcard "*"
        ChatbotResponse response = findResponse(currentState, buttonId, intent);

        if (response == null) {
            // No response defined — escalate silently
            log.warn("No chatbot response found for state={} intent={} buttonId={} — escalating", currentState, intent, buttonId);
            escalateConversation(conversation, content);
            return;
        }

        // Replace {{name}} placeholder
        String lead = conversation.getLead() != null ? conversation.getLead().getName() : "there";
        String messageText = response.getResponseText() != null
                ? response.getResponseText().replace("{{name}}", lead)
                : "";

        // Send the response
        String waMessageId = null;
        try {
            if ("INTERACTIVE_BUTTONS".equals(response.getMessageType()) && response.getButtonsJson() != null) {
                List<Map<String, String>> buttons = parseButtons(response.getButtonsJson());
                waMessageId = whatsAppApiService.sendInteractiveButtonMessage(conversation.getPhone(), messageText, buttons);
            } else if ("TEMPLATE".equals(response.getMessageType())) {
                waMessageId = whatsAppApiService.sendTemplateMessage(conversation.getPhone(), messageText, null);
            } else {
                waMessageId = whatsAppApiService.sendTextMessage(conversation.getPhone(), messageText);
            }
        } catch (Exception e) {
            log.error("Chatbot failed to send message to {}: {}", conversation.getPhone(), e.getMessage());
        }

        // Save outbound message
        WhatsappMessage outbound = WhatsappMessage.builder()
                .conversation(conversation)
                .whatsappMessageId(waMessageId)
                .direction(MessageDirection.OUTBOUND)
                .messageType("INTERACTIVE_BUTTONS".equals(response.getMessageType())
                        ? WaMessageType.INTERACTIVE_BUTTONS : WaMessageType.TEXT)
                .content(messageText)
                .isBotMessage(true)
                .status(WaMessageStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build();
        messageRepository.save(outbound);

        // Update conversation last message
        conversation.setLastMessage("🤖 " + (messageText.length() > 80 ? messageText.substring(0, 80) + "…" : messageText));
        conversation.setLastMessageAt(LocalDateTime.now());

        // Transition state
        if (response.getNextState() != null) {
            try {
                conversation.setChatbotState(ChatbotState.valueOf(response.getNextState()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown next state '{}' — ignoring", response.getNextState());
            }
        }

        // Update lead status if response mandates it
        if (response.getUpdatesLeadStatus() != null && conversation.getLead() != null) {
            try {
                LeadStatus newStatus = LeadStatus.valueOf(response.getUpdatesLeadStatus());
                Lead lead1 = conversation.getLead();
                lead1.setStatus(newStatus);
                leadRepository.save(lead1);
                log.info("Chatbot updated lead {} status to {}", lead1.getId(), newStatus);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown lead status '{}' in chatbot response — ignored", response.getUpdatesLeadStatus());
            }
        }

        // Update session
        ChatbotSession session = chatbotSessionRepository.findByConversation(conversation)
                .orElse(ChatbotSession.builder().conversation(conversation).build());
        session.setCurrentState(conversation.getChatbotState().name());
        session.setLastMessageAt(LocalDateTime.now());
        chatbotSessionRepository.save(session);

        // Log bot activity
        if (conversation.getLead() != null) {
            leadActivityRepository.save(LeadActivity.builder()
                    .lead(conversation.getLead())
                    .activityType(ActivityType.WHATSAPP_BOT_REPLIED)
                    .description("Bot replied: " + response.getState() + " → " + response.getNextState())
                    .build());
        }

        log.info("Chatbot processed: conv={} state={} → {} intent={}",
                conversation.getId(), currentState, conversation.getChatbotState(), intent);
    }

    private String resolveIntent(String buttonId, String content) {
        if (buttonId != null && !buttonId.isBlank()) {
            return buttonId;
        }
        if (content == null) return "ESCALATED";
        String lower = content.toLowerCase();
        if (lower.matches(".*\\b(fee|price|cost|fees|charge|charges|amount)\\b.*")) return "FEE_INQUIRY";
        if (lower.matches(".*\\b(demo|class|attend|join|free class|free demo)\\b.*")) return "DEMO_BOOKING";
        if (lower.matches(".*\\b(location|where|address|office)\\b.*")) return "LOCATION";
        if (lower.matches(".*\\b(timing|time|schedule|batch|when|timings)\\b.*")) return "TIMING";
        if (lower.matches(".*\\b(stop|human|agent|person|support|help me)\\b.*")) return "ESCALATED";
        return "ESCALATED";
    }

    private ChatbotResponse findResponse(String state, String buttonId, String intent) {
        // 1. Exact state + button ID match
        if (buttonId != null) {
            Optional<ChatbotResponse> r = chatbotResponseRepository
                    .findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue(state, "BUTTON_ID", buttonId);
            if (r.isPresent()) return r.get();

            // 2. Wildcard state + button ID
            r = chatbotResponseRepository
                    .findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue("*", "BUTTON_ID", buttonId);
            if (r.isPresent()) return r.get();
        }

        // 3. Exact state + keyword match
        Optional<ChatbotResponse> r = chatbotResponseRepository
                .findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue(state, "KEYWORD", intent);
        if (r.isPresent()) return r.get();

        // 4. Wildcard state + keyword match
        r = chatbotResponseRepository
                .findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue("*", "KEYWORD", intent);
        if (r.isPresent()) return r.get();

        // 5. DEFAULT fallback for current state
        r = chatbotResponseRepository.findByStateAndTriggerTypeAndIsActiveTrue(state, "DEFAULT");
        if (r.isPresent()) return r.get();

        // 6. DEFAULT fallback for wildcard state
        return chatbotResponseRepository.findByStateAndTriggerTypeAndIsActiveTrue("*", "DEFAULT").orElse(null);
    }

    private void escalateConversation(WhatsappConversation conversation, String content) {
        conversation.setStatus(ConversationStatus.NEEDS_HUMAN);
        conversation.setChatbotState(ChatbotState.ESCALATED);
        conversation.setChatbotActive(false);
        try {
            whatsAppApiService.sendTextMessage(conversation.getPhone(),
                    "I'm connecting you with our team. Someone will be with you shortly! 🙏");
        } catch (Exception e) {
            log.error("Could not send escalation message: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseButtons(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse buttons JSON: {}", json);
            return List.of();
        }
    }
}
