package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.*;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final WhatsappConversationRepository conversationRepository;
    private final WhatsappMessageRepository messageRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final ChatbotEngine chatbotEngine;
    private final com.fmt.fmt_backend.repository.UserRepository userRepository;

    @Transactional
    public void handleIncomingMessage(String from, String messageType, String content,
                                       String buttonId, String buttonTitle,
                                       String mediaId, String whatsappMessageId, Long timestamp) {
        // 1. Find or create conversation
        final boolean[] isNew = {false};
        WhatsappConversation conversation = conversationRepository.findByPhone(from)
                .orElseGet(() -> {
                    isNew[0] = true;
                    Lead lead = leadRepository.findByPhone(from).orElseGet(() ->
                            leadRepository.save(Lead.builder()
                                    .name(from)
                                    .phone(from)
                                    .source(LeadSource.WHATSAPP_INBOUND)
                                    .status(LeadStatus.WHATSAPP_RESPONDED)
                                    .build()));
                    return conversationRepository.save(WhatsappConversation.builder()
                            .phone(from)
                            .lead(lead)
                            .entryPoint(ConversationEntryPoint.INBOUND)
                            .build());
                });

        // 1b. Auto round-robin assign new conversations to least-busy SALES user
        if (isNew[0] && conversation.getAssignedTo() == null) {
            java.util.List<com.fmt.fmt_backend.entity.User> salesUsers =
                    userRepository.findAllByUserRoleOrderByCreatedAtDesc(com.fmt.fmt_backend.enums.UserRole.SALES);
            if (!salesUsers.isEmpty()) {
                com.fmt.fmt_backend.entity.User leastBusy = salesUsers.stream()
                        .min(java.util.Comparator.comparingLong(u -> conversationRepository.countActiveByAssignedTo(u)))
                        .orElse(null);
                if (leastBusy != null) {
                    conversation.setAssignedTo(leastBusy);
                    conversation.setStatus(ConversationStatus.ASSIGNED);
                    log.info("New conversation {} auto-assigned to {} (round-robin)", conversation.getId(), leastBusy.getEmail());
                }
            }
        }

        // 2. Refresh 24h messaging window
        conversation.setWindowExpiresAt(LocalDateTime.now().plusHours(24));

        // 3. Update lead reference if missing
        if (conversation.getLead() == null) {
            leadRepository.findByPhone(from).ifPresent(conversation::setLead);
        }

        // 4. Parse message type
        WaMessageType waType = parseMessageType(messageType);

        // 5. Save inbound message
        WhatsappMessage inbound = WhatsappMessage.builder()
                .conversation(conversation)
                .whatsappMessageId(whatsappMessageId)
                .direction(MessageDirection.INBOUND)
                .messageType(waType)
                .content(content)
                .buttonId(buttonId)
                .buttonTitle(buttonTitle)
                .mediaId(mediaId)
                .status(WaMessageStatus.DELIVERED)
                .isBotMessage(false)
                .sentAt(timestamp != null
                        ? java.time.Instant.ofEpochSecond(timestamp)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        : LocalDateTime.now())
                .build();
        messageRepository.save(inbound);

        // 6. Update conversation summary
        conversation.setLastMessage(content != null ? content : buttonTitle);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUnreadCount(conversation.getUnreadCount() + 1);

        // 7. Route to bot or human
        if (Boolean.TRUE.equals(conversation.getChatbotActive())
                && conversation.getStatus() != ConversationStatus.CLOSED) {
            conversationRepository.save(conversation);
            chatbotEngine.processMessage(conversation, content, buttonId, buttonTitle);
            conversationRepository.save(conversation);
        } else {
            conversation.setStatus(ConversationStatus.NEEDS_HUMAN);
            conversationRepository.save(conversation);
            log.info("Conversation {} routed to human (chatbot inactive)", conversation.getId());
        }

        // 8. Log activity
        if (conversation.getLead() != null) {
            leadActivityRepository.save(LeadActivity.builder()
                    .lead(conversation.getLead())
                    .activityType(ActivityType.WHATSAPP_INBOUND)
                    .description("Received: " + (content != null ? content : buttonTitle))
                    .build());
        }

        log.info("Incoming WhatsApp message processed: from={} type={} conv={}",
                from, messageType, conversation.getId());
    }

    @Transactional
    public void updateMessageStatus(String whatsappMessageId, String status, String recipient) {
        messageRepository.findByWhatsappMessageId(whatsappMessageId).ifPresentOrElse(
                msg -> {
                    try {
                        msg.setStatus(WaMessageStatus.valueOf(status.toUpperCase()));
                        messageRepository.save(msg);
                        log.debug("Message {} status updated to {}", whatsappMessageId, status);
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown WA message status '{}' for id={}", status, whatsappMessageId);
                    }
                },
                () -> log.debug("Status update for unknown message id={} — ignored", whatsappMessageId)
        );
    }

    private WaMessageType parseMessageType(String type) {
        if (type == null) return WaMessageType.UNSUPPORTED;
        return switch (type.toLowerCase()) {
            case "text" -> WaMessageType.TEXT;
            case "button" -> WaMessageType.BUTTON_REPLY;
            case "interactive" -> WaMessageType.INTERACTIVE_BUTTONS;
            case "image" -> WaMessageType.IMAGE;
            case "document" -> WaMessageType.DOCUMENT;
            case "audio" -> WaMessageType.AUDIO;
            case "video" -> WaMessageType.VIDEO;
            default -> WaMessageType.UNSUPPORTED;
        };
    }
}
