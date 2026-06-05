package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.TemplateButtonDto;
import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.entity.LeadActivity;
import com.fmt.fmt_backend.entity.WhatsappConversation;
import com.fmt.fmt_backend.entity.WhatsappMessage;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.LeadActivityRepository;
import com.fmt.fmt_backend.repository.LeadRepository;
import com.fmt.fmt_backend.repository.WhatsappMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the action configured for a clicked WhatsApp template button.
 * Called by ChatbotEngine BEFORE the legacy hardcoded state handlers.
 *
 * Implementations mirror (but don't depend on) the existing ChatbotEngine
 * primitives so we avoid a circular dependency. Acceptable code duplication
 * — each method here is ~10 lines.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ButtonActionExecutor {

    private final WhatsAppApiService whatsAppApiService;
    private final WhatsappMessageRepository messageRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final ResendEmailService emailService;

    private static final Set<LeadStatus> PRE_CONTACT = Set.of(
            LeadStatus.NEW, LeadStatus.WHATSAPP_SENT, LeadStatus.WHATSAPP_RESPONDED,
            LeadStatus.IMPORTED, LeadStatus.CAMPAIGN_SENT,
            LeadStatus.DNP_1, LeadStatus.DNP_2, LeadStatus.DNP_3,
            LeadStatus.DNP_4, LeadStatus.DNP_5
    );

    public void execute(WhatsappConversation conversation, TemplateButtonDto button) {
        ButtonActionType type = button.getActionType();
        Map<String, Object> params = button.getActionParams() != null
                ? button.getActionParams()
                : Map.of();

        log.info("Executing button action {} on conv={} payload='{}'",
                type, conversation.getId(), button.getPayload());

        switch (type) {
            case SEND_TEXT          -> doSendText(conversation, str(params, "text"));
            case SEND_TEMPLATE      -> doSendTemplate(conversation, str(params, "templateName"));
            case ESCALATE_TO_HUMAN  -> doEscalate(conversation);
            case RESERVE_SEAT       -> doReserveSeat(conversation, str(params, "batchName"));
            case TRIGGER_DEMO_FLOW  -> doTriggerDemoFlow(conversation);
            case UPDATE_LEAD_STATUS -> doUpdateLeadStatus(conversation, str(params, "status"));
        }
    }

    // ─── Action implementations ──────────────────────────────────────────────────

    private void doSendText(WhatsappConversation conv, String text) {
        if (text == null || text.isBlank()) {
            log.warn("SEND_TEXT action has no 'text' param for conv={}", conv.getId());
            return;
        }
        sendText(conv, text);
    }

    private void doSendTemplate(WhatsappConversation conv, String templateName) {
        if (templateName == null || templateName.isBlank()) {
            log.warn("SEND_TEMPLATE action has no 'templateName' param for conv={}", conv.getId());
            return;
        }
        String waId = null;
        try {
            // Header media id is looked up from DB inside WhatsAppApiService
            waId = whatsAppApiService.sendTemplateMessage(conv.getPhone(), templateName, List.of(), null, null);
        } catch (Exception e) {
            log.error("SEND_TEMPLATE failed for {}: {}", conv.getPhone(), e.getMessage());
        }
        saveOutbound(conv, waId, "[Template: " + templateName + "]", WaMessageType.TEXT);
    }

    private void doEscalate(WhatsappConversation conv) {
        conv.setStatus(ConversationStatus.NEEDS_HUMAN);
        conv.setChatbotActive(false);
        conv.setChatbotState(ChatbotState.ESCALATED);
        sendText(conv,
                "I'm connecting you with our team now. Someone will reach out to you shortly! 🙏\n\n" +
                "📞 You can also reach us at: *+91 90320 46008*");
        log.info("Conversation {} escalated to human via button action", conv.getId());
    }

    private void doReserveSeat(WhatsappConversation conv, String batchName) {
        String name = leadName(conv);
        String batch = (batchName == null || batchName.isBlank()) ? "the upcoming batch" : batchName;

        sendText(conv,
                "🎯 Awesome choice, " + name + "!\n\n" +
                "Your seat in *" + batch + "* is noted. Our team will contact you within 5 minutes " +
                "to confirm your seat and share the payment details.\n\n" +
                "Get ready to start your trading journey! 🚀\n\n" +
                "📞 Or call us directly: *+91 90320 46008*");

        try {
            emailService.sendSeatReservationAlert(name, conv.getPhone());
        } catch (Exception e) {
            log.warn("Failed to send seat reservation email alert: {}", e.getMessage());
        }

        Lead lead = conv.getLead();
        if (lead != null) {
            if (lead.getStatus() == null || PRE_CONTACT.contains(lead.getStatus())) {
                lead.setStatus(LeadStatus.CONTACTED);
            }
            leadRepository.save(lead);
            leadActivityRepository.save(LeadActivity.builder()
                    .lead(lead)
                    .activityType(ActivityType.WHATSAPP_BOT_REPLIED)
                    .description("Reserved seat (" + batch + ") via campaign — escalated to sales")
                    .build());
        }

        conv.setChatbotActive(false);
        conv.setStatus(ConversationStatus.NEEDS_HUMAN);
        conv.setChatbotState(ChatbotState.ESCALATED);
        log.info("Seat reservation via button action: conv={} lead={} batch={}", conv.getId(), name, batch);
    }

    private void doTriggerDemoFlow(WhatsappConversation conv) {
        // Send the demo booking confirm template (date selection buttons).
        // Media id is looked up from DB by WhatsAppApiService.
        String waId = null;
        try {
            waId = whatsAppApiService.sendTemplateMessage(
                    conv.getPhone(),
                    "fmt_demo_booking_confirm",
                    List.of(leadName(conv)),
                    List.of("customer_name"),
                    null);
        } catch (Exception e) {
            log.error("TRIGGER_DEMO_FLOW failed for {}: {}", conv.getPhone(), e.getMessage());
        }
        saveOutbound(conv, waId, "[Template: fmt_demo_booking_confirm]", WaMessageType.TEXT);

        // Move state machine into demo flow — existing handlers (handleDemoDateAsked etc.)
        // pick up from here.
        conv.setChatbotState(ChatbotState.DEMO_DATE_ASKED);
        log.info("TRIGGER_DEMO_FLOW button action set conv={} to DEMO_DATE_ASKED", conv.getId());
    }

    private void doUpdateLeadStatus(WhatsappConversation conv, String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            log.warn("UPDATE_LEAD_STATUS has no 'status' param for conv={}", conv.getId());
            return;
        }
        Lead lead = conv.getLead();
        if (lead == null) {
            log.warn("UPDATE_LEAD_STATUS: conv={} has no lead", conv.getId());
            return;
        }
        try {
            LeadStatus newStatus = LeadStatus.valueOf(statusStr.trim().toUpperCase());
            lead.setStatus(newStatus);
            leadRepository.save(lead);
            log.info("Lead {} status updated to {} via button action", lead.getId(), newStatus);
        } catch (IllegalArgumentException e) {
            log.warn("UPDATE_LEAD_STATUS got invalid status '{}' for conv={}", statusStr, conv.getId());
        }
    }

    // ─── Helpers (mirror ChatbotEngine's private ones) ───────────────────────────

    private void sendText(WhatsappConversation conv, String text) {
        String waId = null;
        try {
            waId = whatsAppApiService.sendTextMessage(conv.getPhone(), text);
        } catch (Exception e) {
            log.error("sendText failed for {}: {}", conv.getPhone(), e.getMessage());
        }
        saveOutbound(conv, waId, text, WaMessageType.TEXT);
        conv.setLastMessage("🤖 " + truncate(text, 80));
        conv.setLastMessageAt(LocalDateTime.now());
    }

    private void saveOutbound(WhatsappConversation conv, String waId, String text, WaMessageType type) {
        messageRepository.save(WhatsappMessage.builder()
                .conversation(conv)
                .whatsappMessageId(waId)
                .direction(MessageDirection.OUTBOUND)
                .messageType(type)
                .content(text)
                .isBotMessage(true)
                .status(WaMessageStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build());
    }

    private static String leadName(WhatsappConversation conv) {
        if (conv.getLead() != null) {
            String name = conv.getLead().getName();
            if (name != null && !name.isBlank() && !name.equals(conv.getPhone())) return name.split(" ")[0];
        }
        return "there";
    }

    private static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
