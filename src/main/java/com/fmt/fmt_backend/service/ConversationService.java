package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.*;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final WhatsappConversationRepository conversationRepository;
    private final WhatsappMessageRepository messageRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final ChatbotEngine chatbotEngine;
    private final ChatbotGlobalSettings chatbotGlobalSettings;
    private final UserRepository userRepository;

    // Lead statuses where bot auto-disables — human should handle these
    private static final Set<LeadStatus> HUMAN_REQUIRED_STATUSES = Set.of(
            LeadStatus.CONTACTED,
            LeadStatus.FOLLOWUP_SCHEDULED,
            LeadStatus.DEMO_BOOKED,
            LeadStatus.DEMO_DONE,
            LeadStatus.DEMO_NO_SHOW,
            LeadStatus.CLOSING,
            LeadStatus.PAYMENT_DONE
    );

    // Bot cooldown: avoid double-reply when WhatsApp sends image+text as two simultaneous events
    private static final int BOT_COOLDOWN_SECONDS = 5;

    @Transactional
    public void handleIncomingMessage(String from, String messageType, String content,
                                       String buttonId, String buttonTitle,
                                       String mediaId, String whatsappMessageId, Long timestamp,
                                       String contactName) {
        // 1. Find or create conversation — try exact phone, then flexible lookup
        final boolean[] isNew = {false};
        WhatsappConversation conversation = conversationRepository.findByPhone(from)
                .orElseGet(() -> {
                    isNew[0] = true;
                    // Use WhatsApp display name if available, otherwise fall back to phone
                    String leadName = (contactName != null && !contactName.isBlank()) ? contactName : from;
                    // Look up lead by exact phone OR 10-digit local form (strips 91 prefix)
                    Lead lead = findLeadByPhoneFlexible(from).orElseGet(() ->
                            leadRepository.save(Lead.builder()
                                    .name(leadName)
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
            List<User> salesUsers = userRepository.findAllByUserRoleOrderByCreatedAtDesc(UserRole.SALES);
            if (!salesUsers.isEmpty()) {
                User leastBusy = salesUsers.stream()
                        .min(Comparator.comparingLong(u -> conversationRepository.countActiveByAssignedTo(u)))
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
            findLeadByPhoneFlexible(from).ifPresent(conversation::setLead);
        }

        // 3b. Backfill lead name if it was previously set to the phone number (unknown)
        if (contactName != null && !contactName.isBlank() && conversation.getLead() != null) {
            Lead lead = conversation.getLead();
            if (lead.getName() == null || lead.getName().equals(from) || lead.getName().equals(from.substring(Math.max(0, from.length() - 10)))) {
                lead.setName(contactName);
                leadRepository.save(lead);
                log.info("Backfilled lead {} name from WhatsApp profile: {}", lead.getId(), contactName);
            }
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
        BotDecision botDecision = decideBotAction(conversation, waType);
        if (botDecision == BotDecision.RUN) {
            conversationRepository.save(conversation);
            chatbotEngine.processMessage(conversation, content, buttonId, buttonTitle);
            conversationRepository.save(conversation);
        } else if (botDecision == BotDecision.NEEDS_HUMAN) {
            conversation.setStatus(ConversationStatus.NEEDS_HUMAN);
            conversationRepository.save(conversation);
        } else {
            // COOLDOWN — message saved but bot silently skips, no status change
            conversationRepository.save(conversation);
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

    private enum BotDecision { RUN, NEEDS_HUMAN, COOLDOWN }

    private BotDecision decideBotAction(WhatsappConversation conv, WaMessageType messageType) {
        // Global off switch
        if (!chatbotGlobalSettings.isEnabled()) {
            log.debug("Bot globally disabled — routing to human");
            return BotDecision.NEEDS_HUMAN;
        }
        // Per-chat toggle already off
        if (!Boolean.TRUE.equals(conv.getChatbotActive())) return BotDecision.NEEDS_HUMAN;
        // Closed conversations never get bot
        if (conv.getStatus() == ConversationStatus.CLOSED) return BotDecision.NEEDS_HUMAN;

        // Smart auto-disable: if lead is in a warm/mid stage, turn off bot
        Lead lead = conv.getLead();
        if (lead != null && lead.getStatus() != null && HUMAN_REQUIRED_STATUSES.contains(lead.getStatus())) {
            log.info("Lead {} is in status {} — auto-disabling bot for conversation {}",
                    lead.getId(), lead.getStatus(), conv.getId());
            conv.setChatbotActive(false);
            return BotDecision.NEEDS_HUMAN;
        }

        // Cooldown: skip bot if we already replied in the last 45s (handles image+text double-fire).
        // Button replies are explicit user actions — never throttle them.
        if (messageType != WaMessageType.BUTTON_REPLY && messageType != WaMessageType.INTERACTIVE_BUTTONS) {
            boolean recentReply = messageRepository.existsRecentBotMessage(
                    conv, LocalDateTime.now().minusSeconds(BOT_COOLDOWN_SECONDS));
            if (recentReply) {
                log.info("Bot cooldown active for conversation {} — skipping auto-reply", conv.getId());
                return BotDecision.COOLDOWN;
            }
        }

        return BotDecision.RUN;
    }

    private Optional<Lead> findLeadByPhoneFlexible(String phone) {
        Optional<Lead> found = leadRepository.findByPhone(phone);
        if (found.isPresent()) return found;
        // WhatsApp sends 919XXXXXXXXX; lead may be stored as 9XXXXXXXXX (without 91 prefix)
        if (phone.length() == 12 && phone.startsWith("91")) {
            found = leadRepository.findByPhone(phone.substring(2));
            if (found.isPresent()) return found;
        }
        // Lead stored with 91 prefix, incoming without
        if (phone.length() == 10) {
            found = leadRepository.findByPhone("91" + phone);
        }
        return found;
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
            case "sticker" -> WaMessageType.IMAGE; // stickers render like images
            default -> WaMessageType.UNSUPPORTED;
        };
    }
}
