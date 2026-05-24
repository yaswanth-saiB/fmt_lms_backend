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
import java.util.*;

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
    public void processMessage(WhatsappConversation conversation, String content,
                                String buttonId, String buttonTitle) {
        ChatbotState state = conversation.getChatbotState();
        log.info("Bot processing: conv={} state={} buttonId={}", conversation.getId(), state, buttonId);

        switch (state) {
            case INITIAL, INITIAL_LEAD_GEN -> handleInitial(conversation);
            case MENU_SHOWN -> handleMenuShown(conversation, content, buttonId);
            case COURSE_SELECTION -> handleCourseSelection(conversation, content, buttonId);
            case DEMO_DATE_ASKED -> handleDemoDateAsked(conversation, content, buttonId);
            case DEMO_CUSTOM_DATE -> handleDemoCustomDate(conversation, content);
            case DEMO_MODE_ASKED -> handleDemoModeAsked(conversation, content, buttonId);
            case DEMO_TIME_ASKED -> handleDemoTimeAsked(conversation, content, buttonId);
            case DEMO_CONFIRMED, ESCALATED, CLOSED ->
                    log.debug("No bot action for terminal state {}", state);
            default -> handleWithDbFallback(conversation, content, buttonId);
        }

        updateSession(conversation);
    }

    // ─── State Handlers ──────────────────────────────────────────────────────────

    private void handleInitial(WhatsappConversation conversation) {
        String name = leadName(conversation);
        sendText(conversation,
                "👋 Hi " + name + "! Welcome to *First Million Trade* — India's premier trading education platform.\n\n" +
                "We help you master Stock Markets, Forex & Options Trading with expert mentorship. 🚀");
        sendMenu(conversation);
        transition(conversation, ChatbotState.MENU_SHOWN);
    }

    private void handleMenuShown(WhatsappConversation conversation, String content, String buttonId) {
        String trigger = buttonId != null ? buttonId.toUpperCase() : resolveMenuKeyword(content);

        switch (trigger) {
            case "MENU_COURSES" -> {
                sendCourseMenu(conversation);
                transition(conversation, ChatbotState.COURSE_SELECTION);
            }
            case "MENU_DEMO" -> {
                sendTemplate(conversation, "fmt_demo_booking_confirm", List.of(leadName(conversation)));
                sendDateButtons(conversation);
                transition(conversation, ChatbotState.DEMO_DATE_ASKED);
            }
            case "MENU_FEE" -> {
                sendText(conversation,
                        "📚 Our courses are designed to deliver real, practical value.\n\n" +
                        "To get the exact fee structure and batch details for your chosen program, " +
                        "our advisor will reach out to you shortly!\n\n" +
                        "📞 Or call us directly: *+91 90320 46008*");
                escalate(conversation);
            }
            case "TALK_HUMAN", "ESCALATED" -> escalate(conversation);
            default -> handleUnknown(conversation, content);
        }
    }

    private void handleCourseSelection(WhatsappConversation conversation, String content, String buttonId) {
        String input = (buttonId != null ? buttonId : content != null ? content : "").trim().toUpperCase();

        if (input.matches("COURSE_HIT|1|HIT|HIT PROGRAM|HIT TRADING")) {
            sendTemplate(conversation, "fmt_hit_program_details", List.of());
            sendMenu(conversation);
            transition(conversation, ChatbotState.MENU_SHOWN);

        } else if (input.matches("COURSE_OPTIONS|2|OPTIONS|OPTIONS TRADING")) {
            sendText(conversation,
                    "📈 *Options Trading Program*\n\n" +
                    "Master professional options strategies for F&O markets.\n\n" +
                    "✅ Covered Call, Put Spreads, Iron Condor\n" +
                    "✅ Options Greeks — Delta, Gamma, Theta, Vega\n" +
                    "✅ Live market trade setups\n\n" +
                    "Our advisor will contact you with the full syllabus and batch schedule!");
            sendMenu(conversation);
            transition(conversation, ChatbotState.MENU_SHOWN);

        } else if (input.matches("COURSE_FOREX|3|FOREX|FOREX TRADING")) {
            sendTemplate(conversation, "fmt_forex_program_details", List.of());
            sendMenu(conversation);
            transition(conversation, ChatbotState.MENU_SHOWN);

        } else if (input.matches("COURSE_STOCK|4|STOCK|STOCK MARKET|EQUITY")) {
            sendText(conversation,
                    "📊 *Stock Market Program*\n\n" +
                    "From basics to advanced — learn to pick stocks and build a portfolio.\n\n" +
                    "✅ Fundamental + Technical Analysis\n" +
                    "✅ Portfolio building strategies\n" +
                    "✅ Risk management framework\n\n" +
                    "Our advisor will contact you with the full program details!");
            sendMenu(conversation);
            transition(conversation, ChatbotState.MENU_SHOWN);

        } else if (input.matches("COURSE_SMC|5|SMC|SMART MONEY|SMART MONEY CONCEPTS")) {
            sendText(conversation,
                    "💡 *Smart Money Concepts (SMC)*\n\n" +
                    "Trade alongside institutional money and understand real market structure.\n\n" +
                    "✅ Order Blocks & Liquidity Zones\n" +
                    "✅ Break of Structure / CHOCH\n" +
                    "✅ Premium & Discount zone trading\n\n" +
                    "Our team will contact you with the full program details!");
            sendMenu(conversation);
            transition(conversation, ChatbotState.MENU_SHOWN);

        } else {
            handleUnknown(conversation, content);
        }
    }

    private void handleDemoDateAsked(WhatsappConversation conversation, String content, String buttonId) {
        String input = (buttonId != null ? buttonId : content != null ? content : "").toUpperCase();

        if (input.equals("DEMO_TODAY") || input.contains("TODAY")) {
            setSavedData(conversation, "demoDate", "Today");
            sendModeButtons(conversation);
            transition(conversation, ChatbotState.DEMO_MODE_ASKED);
        } else if (input.equals("DEMO_TOMORROW") || input.contains("TOMORROW")) {
            setSavedData(conversation, "demoDate", "Tomorrow");
            sendModeButtons(conversation);
            transition(conversation, ChatbotState.DEMO_MODE_ASKED);
        } else if (input.equals("DEMO_CUSTOM") || input.contains("PICK") || input.contains("ANOTHER")) {
            sendText(conversation, "📅 Please type your preferred date (e.g. *25 May* or *next Monday*):");
            transition(conversation, ChatbotState.DEMO_CUSTOM_DATE);
        } else {
            handleUnknown(conversation, content);
        }
    }

    private void handleDemoCustomDate(WhatsappConversation conversation, String content) {
        if (content == null || content.isBlank()) {
            sendText(conversation, "📅 Please type your preferred date (e.g. *25 May*):");
            return;
        }
        setSavedData(conversation, "demoDate", content.trim());
        sendModeButtons(conversation);
        transition(conversation, ChatbotState.DEMO_MODE_ASKED);
    }

    private void handleDemoModeAsked(WhatsappConversation conversation, String content, String buttonId) {
        String input = (buttonId != null ? buttonId : content != null ? content : "").toUpperCase();

        if (input.equals("DEMO_ONLINE") || input.contains("ONLINE")) {
            setSavedData(conversation, "demoMode", "Online");
            sendTimeButtons(conversation);
            transition(conversation, ChatbotState.DEMO_TIME_ASKED);
        } else if (input.equals("DEMO_OFFLINE") || input.contains("OFFLINE")) {
            setSavedData(conversation, "demoMode", "Offline");
            sendTimeButtons(conversation);
            transition(conversation, ChatbotState.DEMO_TIME_ASKED);
        } else {
            handleUnknown(conversation, content);
        }
    }

    private void handleDemoTimeAsked(WhatsappConversation conversation, String content, String buttonId) {
        String input = (buttonId != null ? buttonId : content != null ? content : "").toUpperCase();

        String time = null;
        if (input.equals("DEMO_MORNING") || input.contains("MORNING")) time = "Morning";
        else if (input.equals("DEMO_EVENING") || input.contains("EVENING")) time = "Evening";
        else if (input.equals("DEMO_NIGHT") || input.contains("NIGHT")) time = "Night";

        if (time != null) {
            setSavedData(conversation, "demoTime", time);
            confirmDemoBooking(conversation);
        } else {
            handleUnknown(conversation, content);
        }
    }

    private void confirmDemoBooking(WhatsappConversation conversation) {
        Map<String, String> data = getSavedData(conversation);
        String date = data.getOrDefault("demoDate", "a date to be confirmed");
        String mode = data.getOrDefault("demoMode", "Online");
        String time = data.getOrDefault("demoTime", "Morning");
        String name = leadName(conversation);

        String zoomOrVenue = mode.equals("Online") ? " and Zoom link" : " and venue address";
        sendText(conversation, String.format(
                "✅ *Demo Class Booked!*\n\n" +
                "Hi %s, your free demo class is confirmed:\n\n" +
                "📅 *Date:* %s\n" +
                "📍 *Mode:* %s\n" +
                "⏰ *Time:* %s\n\n" +
                "Our team will reach out to confirm the exact time%s. See you soon! 🎉\n\n" +
                "📞 Questions? Call us: *+91 90320 46008*",
                name, date, mode, time, zoomOrVenue));

        Lead lead = conversation.getLead();
        if (lead != null) {
            lead.setChatbotDemoDate(date);
            lead.setChatbotDemoMode(mode);
            lead.setChatbotDemoTime(time);
            lead.setChatbotDemoBookedAt(LocalDateTime.now());
            lead.setStatus(LeadStatus.DEMO_BOOKED);
            lead.setDemoType(mode.equalsIgnoreCase("Online") ? DemoType.ONLINE : DemoType.OFFLINE);
            leadRepository.save(lead);

            leadActivityRepository.save(LeadActivity.builder()
                    .lead(lead)
                    .activityType(ActivityType.WHATSAPP_BOT_REPLIED)
                    .description("Demo booked via chatbot — " + date + " | " + mode + " | " + time)
                    .build());
        }

        transition(conversation, ChatbotState.DEMO_CONFIRMED);
        conversation.setChatbotActive(false);
        conversation.setStatus(ConversationStatus.NEEDS_HUMAN);
        log.info("Demo booked via chatbot: conv={} date={} mode={} time={}", conversation.getId(), date, mode, time);
    }

    // ─── Unknown / Fallback ──────────────────────────────────────────────────────

    private void handleUnknown(WhatsappConversation conversation, String content) {
        Map<String, String> data = getSavedData(conversation);
        int unknownCount = Integer.parseInt(data.getOrDefault("unknownCount", "0")) + 1;

        if (unknownCount >= 2) {
            sendText(conversation,
                    "I'm not sure I understood that 😊 Let me connect you with our team for better help!");
            escalate(conversation);
        } else {
            setSavedData(conversation, "unknownCount", String.valueOf(unknownCount));
            sendText(conversation,
                    "Sorry, I didn't quite get that. Please use the menu options or type your query clearly.");
            sendMenu(conversation);
        }
    }

    private void handleWithDbFallback(WhatsappConversation conversation, String content, String buttonId) {
        String state = conversation.getChatbotState().name();
        String intent = resolveIntent(buttonId, content);
        ChatbotResponse response = findDbResponse(state, buttonId, intent);

        if (response == null) {
            log.warn("No DB chatbot response for state={} — escalating", state);
            escalate(conversation);
            return;
        }

        String text = response.getResponseText() != null
                ? response.getResponseText().replace("{{name}}", leadName(conversation))
                : "";
        String waId = null;
        try {
            if ("INTERACTIVE_BUTTONS".equals(response.getMessageType()) && response.getButtonsJson() != null) {
                waId = whatsAppApiService.sendInteractiveButtonMessage(
                        conversation.getPhone(), text, parseButtons(response.getButtonsJson()));
            } else if ("TEMPLATE".equals(response.getMessageType())) {
                waId = whatsAppApiService.sendTemplateMessage(conversation.getPhone(), text, null);
            } else {
                waId = whatsAppApiService.sendTextMessage(conversation.getPhone(), text);
            }
        } catch (Exception e) {
            log.error("DB fallback send failed for {}: {}", conversation.getPhone(), e.getMessage());
        }

        saveOutbound(conversation, waId, text, "INTERACTIVE_BUTTONS".equals(response.getMessageType())
                ? WaMessageType.INTERACTIVE_BUTTONS : WaMessageType.TEXT);
        conversation.setLastMessage("🤖 " + truncate(text, 80));
        conversation.setLastMessageAt(LocalDateTime.now());

        if (response.getNextState() != null) {
            try {
                transition(conversation, ChatbotState.valueOf(response.getNextState()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown next state '{}'", response.getNextState());
            }
        }
        if (response.getUpdatesLeadStatus() != null && conversation.getLead() != null) {
            try {
                conversation.getLead().setStatus(LeadStatus.valueOf(response.getUpdatesLeadStatus()));
                leadRepository.save(conversation.getLead());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown lead status '{}'", response.getUpdatesLeadStatus());
            }
        }
    }

    // ─── Message Senders ─────────────────────────────────────────────────────────

    private void sendMenu(WhatsappConversation conversation) {
        String body = "How can we help you today? 😊";
        List<Map<String, String>> buttons = List.of(
                Map.of("id", "MENU_COURSES", "title", "Our Courses"),
                Map.of("id", "MENU_DEMO", "title", "Free Demo Class"),
                Map.of("id", "MENU_FEE", "title", "Know Fee Details"));
        String waId = null;
        try {
            waId = whatsAppApiService.sendInteractiveButtonMessage(conversation.getPhone(), body, buttons);
        } catch (Exception e) {
            log.error("sendMenu failed: {}", e.getMessage());
        }
        saveOutbound(conversation, waId, body, WaMessageType.INTERACTIVE_BUTTONS);
        conversation.setLastMessage("🤖 " + body);
        conversation.setLastMessageAt(LocalDateTime.now());
    }

    private void sendCourseMenu(WhatsappConversation conversation) {
        sendText(conversation,
                "📚 *Courses at First Million Trade:*\n\n" +
                "1️⃣ HIT Trading Program\n" +
                "2️⃣ Options Trading\n" +
                "3️⃣ Forex Trading\n" +
                "4️⃣ Stock Market\n" +
                "5️⃣ Smart Money Concepts (SMC)\n\n" +
                "Reply with the *number* or *course name* to know more!");
    }

    private void sendDateButtons(WhatsappConversation conversation) {
        String body = "📅 Which day works for your free demo class?";
        List<Map<String, String>> buttons = List.of(
                Map.of("id", "DEMO_TODAY", "title", "Today"),
                Map.of("id", "DEMO_TOMORROW", "title", "Tomorrow"),
                Map.of("id", "DEMO_CUSTOM", "title", "Pick Another Day"));
        String waId = null;
        try {
            waId = whatsAppApiService.sendInteractiveButtonMessage(conversation.getPhone(), body, buttons);
        } catch (Exception e) {
            log.error("sendDateButtons failed: {}", e.getMessage());
        }
        saveOutbound(conversation, waId, body, WaMessageType.INTERACTIVE_BUTTONS);
        conversation.setLastMessage("🤖 " + body);
        conversation.setLastMessageAt(LocalDateTime.now());
    }

    private void sendModeButtons(WhatsappConversation conversation) {
        String body = "Would you prefer an *Online* or *Offline* demo class?";
        List<Map<String, String>> buttons = List.of(
                Map.of("id", "DEMO_ONLINE", "title", "Online"),
                Map.of("id", "DEMO_OFFLINE", "title", "Offline"));
        String waId = null;
        try {
            waId = whatsAppApiService.sendInteractiveButtonMessage(conversation.getPhone(), body, buttons);
        } catch (Exception e) {
            log.error("sendModeButtons failed: {}", e.getMessage());
        }
        saveOutbound(conversation, waId, body, WaMessageType.INTERACTIVE_BUTTONS);
        conversation.setLastMessage("🤖 " + body);
        conversation.setLastMessageAt(LocalDateTime.now());
    }

    private void sendTimeButtons(WhatsappConversation conversation) {
        String body = "⏰ What time slot works best for you?";
        List<Map<String, String>> buttons = List.of(
                Map.of("id", "DEMO_MORNING", "title", "Morning"),
                Map.of("id", "DEMO_EVENING", "title", "Evening"),
                Map.of("id", "DEMO_NIGHT", "title", "Night"));
        String waId = null;
        try {
            waId = whatsAppApiService.sendInteractiveButtonMessage(conversation.getPhone(), body, buttons);
        } catch (Exception e) {
            log.error("sendTimeButtons failed: {}", e.getMessage());
        }
        saveOutbound(conversation, waId, body, WaMessageType.INTERACTIVE_BUTTONS);
        conversation.setLastMessage("🤖 " + body);
        conversation.setLastMessageAt(LocalDateTime.now());
    }

    private void sendText(WhatsappConversation conversation, String text) {
        String waId = null;
        try {
            waId = whatsAppApiService.sendTextMessage(conversation.getPhone(), text);
        } catch (Exception e) {
            log.error("sendText failed for {}: {}", conversation.getPhone(), e.getMessage());
        }
        saveOutbound(conversation, waId, text, WaMessageType.TEXT);
        conversation.setLastMessage("🤖 " + truncate(text, 80));
        conversation.setLastMessageAt(LocalDateTime.now());
    }

    private void sendTemplate(WhatsappConversation conversation, String templateName, List<String> params) {
        String waId = null;
        try {
            waId = whatsAppApiService.sendTemplateMessage(conversation.getPhone(), templateName, params);
        } catch (Exception e) {
            log.error("sendTemplate '{}' failed for {}: {}", templateName, conversation.getPhone(), e.getMessage());
        }
        String displayText = "[Template: " + templateName + "]";
        saveOutbound(conversation, waId, displayText, WaMessageType.TEXT);
        conversation.setLastMessage("🤖 " + displayText);
        conversation.setLastMessageAt(LocalDateTime.now());
    }

    private void escalate(WhatsappConversation conversation) {
        conversation.setStatus(ConversationStatus.NEEDS_HUMAN);
        conversation.setChatbotActive(false);
        transition(conversation, ChatbotState.ESCALATED);
        sendText(conversation,
                "I'm connecting you with our team now. Someone will reach out to you shortly! 🙏\n\n" +
                "📞 You can also reach us at: *+91 90320 46008*");
        log.info("Conversation {} escalated to human", conversation.getId());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void transition(WhatsappConversation conversation, ChatbotState newState) {
        ChatbotState old = conversation.getChatbotState();
        conversation.setChatbotState(newState);
        if (old != newState) {
            setSavedData(conversation, "unknownCount", "0");
        }
    }

    private void saveOutbound(WhatsappConversation conversation, String waId, String text, WaMessageType type) {
        messageRepository.save(WhatsappMessage.builder()
                .conversation(conversation)
                .whatsappMessageId(waId)
                .direction(MessageDirection.OUTBOUND)
                .messageType(type)
                .content(text)
                .isBotMessage(true)
                .status(WaMessageStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build());
    }

    private void updateSession(WhatsappConversation conversation) {
        ChatbotSession session = chatbotSessionRepository.findByConversation(conversation)
                .orElse(ChatbotSession.builder().conversation(conversation).build());
        session.setCurrentState(conversation.getChatbotState().name());
        session.setLastMessageAt(LocalDateTime.now());
        chatbotSessionRepository.save(session);
    }

    private String leadName(WhatsappConversation conversation) {
        Lead lead = conversation.getLead();
        if (lead == null) return "there";
        String name = lead.getName();
        if (name != null && !name.equals(conversation.getPhone())) {
            return name.split("\\s+")[0];
        }
        return "there";
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private String resolveMenuKeyword(String content) {
        if (content == null) return "UNKNOWN";
        String lower = content.toLowerCase().trim();
        if (lower.matches(".*\\b(course|courses|program|programs|learn|curriculum)\\b.*")) return "MENU_COURSES";
        if (lower.matches(".*\\b(demo|free demo|free class|trial|attend|join)\\b.*")) return "MENU_DEMO";
        if (lower.matches(".*\\b(fee|fees|price|cost|charges|amount|how much)\\b.*")) return "MENU_FEE";
        if (lower.matches(".*\\b(human|agent|person|support|help|talk|call|contact)\\b.*")) return "TALK_HUMAN";
        return "UNKNOWN";
    }

    // ─── savedData JSON helpers ───────────────────────────────────────────────────

    private Map<String, String> getSavedData(WhatsappConversation conversation) {
        String raw = conversation.getSavedData();
        if (raw == null || raw.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(raw, new TypeReference<HashMap<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse savedData for conv {}: {}", conversation.getId(), raw);
            return new HashMap<>();
        }
    }

    private void setSavedData(WhatsappConversation conversation, String key, String value) {
        Map<String, String> data = getSavedData(conversation);
        data.put(key, value);
        try {
            conversation.setSavedData(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            log.error("Failed to serialize savedData: {}", e.getMessage());
        }
    }

    // ─── DB-Driven Helpers ────────────────────────────────────────────────────────

    private String resolveIntent(String buttonId, String content) {
        if (buttonId != null && !buttonId.isBlank()) return buttonId;
        if (content == null) return "UNKNOWN";
        String lower = content.toLowerCase();
        if (lower.matches(".*\\b(fee|price|cost|fees|charge|charges|amount)\\b.*")) return "FEE_INQUIRY";
        if (lower.matches(".*\\b(demo|class|attend|join|free class|free demo)\\b.*")) return "DEMO_BOOKING";
        if (lower.matches(".*\\b(location|where|address|office)\\b.*")) return "LOCATION";
        if (lower.matches(".*\\b(timing|time|schedule|batch|when|timings)\\b.*")) return "TIMING";
        if (lower.matches(".*\\b(stop|human|agent|person|support|help me)\\b.*")) return "ESCALATED";
        return "UNKNOWN";
    }

    private ChatbotResponse findDbResponse(String state, String buttonId, String intent) {
        if (buttonId != null) {
            var r = chatbotResponseRepository.findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue(
                    state, "BUTTON_ID", buttonId);
            if (r.isPresent()) return r.get();
            r = chatbotResponseRepository.findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue(
                    "*", "BUTTON_ID", buttonId);
            if (r.isPresent()) return r.get();
        }
        var r = chatbotResponseRepository.findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue(
                state, "KEYWORD", intent);
        if (r.isPresent()) return r.get();
        r = chatbotResponseRepository.findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue(
                "*", "KEYWORD", intent);
        if (r.isPresent()) return r.get();
        r = chatbotResponseRepository.findByStateAndTriggerTypeAndIsActiveTrue(state, "DEFAULT");
        if (r.isPresent()) return r.get();
        return chatbotResponseRepository.findByStateAndTriggerTypeAndIsActiveTrue("*", "DEFAULT").orElse(null);
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
