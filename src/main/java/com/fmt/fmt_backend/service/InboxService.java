package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.*;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxService {

    private final WhatsappConversationRepository conversationRepository;
    private final WhatsappMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final WhatsAppApiService whatsAppApiService;
    private final ConversationNoteRepository noteRepository;
    private final QuickReplyRepository quickReplyRepository;

    // ── List conversations ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> getConversations(
            ConversationStatus status, boolean assignedToMe, String search,
            UUID requestingUserId, int page, int size, String label) {

        UUID assignedToId = assignedToMe ? requestingUserId : null;
        String searchPattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%" : null;
        String labelPattern = (label != null && !label.isBlank())
                ? "%" + label + "%" : null;
        PageRequest pageable = PageRequest.of(page, size);

        return conversationRepository.findWithFilters(status, assignedToId, searchPattern, labelPattern, pageable)
                .map(this::toSummary);
    }

    // ── Get conversation detail ────────────────────────────────────────────────

    @Transactional
    public ConversationDetailResponse getConversationDetail(UUID conversationId) {
        WhatsappConversation conv = findConversation(conversationId);

        conv.setUnreadCount(0);
        conversationRepository.save(conv);

        List<MessageResponse> messages = messageRepository
                .findByConversationOrderBySentAtAsc(conv)
                .stream().map(this::toMessageResponse).collect(Collectors.toList());

        List<NoteResponse> notes = noteRepository
                .findByConversationOrderByCreatedAtDesc(conv)
                .stream().map(this::toNoteResponse).collect(Collectors.toList());

        Lead lead = conv.getLead();
        return ConversationDetailResponse.builder()
                .id(conv.getId())
                .status(conv.getStatus())
                .chatbotActive(conv.getChatbotActive())
                .chatbotState(conv.getChatbotState().name())
                .windowExpiresAt(conv.getWindowExpiresAt())
                .minutesLeftInWindow(minutesLeft(conv.getWindowExpiresAt()))
                .assignedToId(conv.getAssignedTo() != null ? conv.getAssignedTo().getId() : null)
                .assignedToName(conv.getAssignedTo() != null
                        ? conv.getAssignedTo().getFirstName() + " " + conv.getAssignedTo().getLastName() : null)
                .labels(conv.getLabels())
                .leadId(lead != null ? lead.getId() : null)
                .leadName(lead != null ? lead.getName() : conv.getPhone())
                .phone(conv.getPhone())
                .leadStatus(lead != null ? lead.getStatus() : null)
                .courseInterest(lead != null ? lead.getCourseInterest() : null)
                .leadEmail(lead != null ? lead.getEmail() : null)
                .leadAssignedSalesName(lead != null && lead.getAssignedTo() != null
                        ? lead.getAssignedTo().getFirstName() + " " + lead.getAssignedTo().getLastName() : null)
                .leadCrmNotes(lead != null ? lead.getNotes() : null)
                .leadFollowupAt(lead != null ? lead.getFollowupDatetime() : null)
                .leadLastCallAt(lead != null ? lead.getLastCallAt() : null)
                .leadCourseFee(lead != null ? lead.getCourseFee() : null)
                .messages(messages)
                .notes(notes)
                .build();
    }

    // ── Reply ──────────────────────────────────────────────────────────────────

    @Transactional
    public MessageResponse reply(UUID conversationId, String message, UUID sentByUserId, UUID replyToMessageId) {
        WhatsappConversation conv = findConversation(conversationId);

        if (conv.getWindowExpiresAt() == null || conv.getWindowExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "24-hour messaging window has expired. Use send-template to re-engage.");
        }

        String contextWaId = null;
        if (replyToMessageId != null) {
            contextWaId = messageRepository.findById(replyToMessageId)
                    .map(m -> m.getWhatsappMessageId()).orElse(null);
        }
        String waMessageId = whatsAppApiService.sendTextMessage(conv.getPhone(), message, contextWaId);

        User sentBy = userRepository.findById(sentByUserId).orElse(null);
        WhatsappMessage msg = WhatsappMessage.builder()
                .conversation(conv)
                .whatsappMessageId(waMessageId)
                .direction(MessageDirection.OUTBOUND)
                .messageType(WaMessageType.TEXT)
                .content(message)
                .isBotMessage(false)
                .status(WaMessageStatus.SENT)
                .sentBy(sentBy)
                .sentAt(LocalDateTime.now())
                .build();
        messageRepository.save(msg);

        conv.setChatbotActive(false);
        conv.setStatus(ConversationStatus.ASSIGNED);
        conv.setLastMessage(message);
        conv.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conv);

        if (conv.getLead() != null) {
            leadActivityRepository.save(LeadActivity.builder()
                    .lead(conv.getLead())
                    .activityType(ActivityType.WHATSAPP_OUTBOUND)
                    .description("Replied: " + message)
                    .createdBy(sentBy)
                    .build());
        }

        return toMessageResponse(msg);
    }

    // ── Send template ──────────────────────────────────────────────────────────

    @Transactional
    public MessageResponse sendTemplate(UUID conversationId, String templateName,
                                         List<String> parameters, List<String> paramNames, UUID sentByUserId) {
        WhatsappConversation conv = findConversation(conversationId);

        List<String> names = (paramNames != null && !paramNames.isEmpty()) ? paramNames : null;
        String waMessageId = whatsAppApiService.sendTemplateMessage(conv.getPhone(), templateName, parameters, names, null);

        User sentBy = userRepository.findById(sentByUserId).orElse(null);
        WhatsappMessage msg = WhatsappMessage.builder()
                .conversation(conv)
                .whatsappMessageId(waMessageId)
                .direction(MessageDirection.OUTBOUND)
                .messageType(WaMessageType.TEMPLATE)
                .content(templateName)
                .templateName(templateName)
                .isBotMessage(false)
                .status(WaMessageStatus.SENT)
                .sentBy(sentBy)
                .sentAt(LocalDateTime.now())
                .build();
        messageRepository.save(msg);

        conv.setLastMessage("Template: " + templateName);
        conv.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conv);

        return toMessageResponse(msg);
    }

    // ── Assign ─────────────────────────────────────────────────────────────────

    @Transactional
    public void assignConversation(UUID conversationId, UUID assignedToId) {
        WhatsappConversation conv = findConversation(conversationId);
        User assignee = userRepository.findById(assignedToId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        conv.setAssignedTo(assignee);
        conv.setStatus(ConversationStatus.ASSIGNED);
        conversationRepository.save(conv);
        log.info("Conversation {} assigned to {}", conversationId, assignedToId);
    }

    // ── Update status ──────────────────────────────────────────────────────────

    @Transactional
    public void updateStatus(UUID conversationId, ConversationStatus status) {
        WhatsappConversation conv = findConversation(conversationId);
        conv.setStatus(status);
        if (status == ConversationStatus.CLOSED) {
            conv.setChatbotActive(false);
            conv.setChatbotState(ChatbotState.CLOSED);
        }
        conversationRepository.save(conv);
    }

    // ── Update labels ──────────────────────────────────────────────────────────

    @Transactional
    public void updateLabels(UUID conversationId, List<String> labels) {
        WhatsappConversation conv = findConversation(conversationId);
        conv.setLabels(labels == null || labels.isEmpty() ? null
                : labels.stream().map(String::trim).filter(s -> !s.isBlank())
                        .collect(Collectors.joining(",")));
        conversationRepository.save(conv);
    }

    // ── Toggle chatbot ─────────────────────────────────────────────────────────

    @Transactional
    public boolean toggleBot(UUID conversationId) {
        WhatsappConversation conv = findConversation(conversationId);
        boolean newState = !Boolean.TRUE.equals(conv.getChatbotActive());
        conv.setChatbotActive(newState);
        if (newState) {
            conv.setChatbotState(ChatbotState.INITIAL);
            conv.setStatus(ConversationStatus.OPEN);
        }
        conversationRepository.save(conv);
        log.info("Conversation {} chatbot toggled to {}", conversationId, newState);
        return newState;
    }

    // ── Unread count ───────────────────────────────────────────────────────────

    public UnreadCountResponse getUnreadCount(UUID userId, UserRole role) {
        long total = role == UserRole.ADMIN
                ? conversationRepository.countWithUnread()
                : (conversationRepository.sumUnreadForUser(userId) != null
                        ? conversationRepository.sumUnreadForUser(userId) : 0L);
        long needsHuman = conversationRepository.countNeedsHuman();
        return UnreadCountResponse.builder().total(total).needsHuman(needsHuman).build();
    }

    // ── Notes ──────────────────────────────────────────────────────────────────

    @Transactional
    public NoteResponse addNote(UUID conversationId, String content, UUID userId) {
        WhatsappConversation conv = findConversation(conversationId);
        User createdBy = userRepository.findById(userId).orElse(null);
        ConversationNote note = ConversationNote.builder()
                .conversation(conv)
                .content(content)
                .createdBy(createdBy)
                .build();
        note = noteRepository.save(note);
        return toNoteResponse(note);
    }

    @Transactional
    public void deleteNote(UUID noteId) {
        noteRepository.deleteById(noteId);
    }

    // ── Quick replies ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuickReplyResponse> getQuickReplies() {
        return quickReplyRepository.findAllByOrderByTitleAsc()
                .stream().map(this::toQuickReplyResponse).collect(Collectors.toList());
    }

    @Transactional
    public QuickReplyResponse createQuickReply(QuickReplyRequest req, UUID userId) {
        User createdBy = userRepository.findById(userId).orElse(null);
        QuickReply qr = QuickReply.builder()
                .title(req.getTitle())
                .content(req.getContent())
                .createdBy(createdBy)
                .build();
        qr = quickReplyRepository.save(qr);
        return toQuickReplyResponse(qr);
    }

    @Transactional
    public QuickReplyResponse updateQuickReply(UUID id, QuickReplyRequest req) {
        QuickReply qr = quickReplyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quick reply not found"));
        qr.setTitle(req.getTitle());
        qr.setContent(req.getContent());
        qr = quickReplyRepository.save(qr);
        return toQuickReplyResponse(qr);
    }

    @Transactional
    public void deleteQuickReply(UUID id) {
        quickReplyRepository.deleteById(id);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private WhatsappConversation findConversation(UUID id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private ConversationSummaryResponse toSummary(WhatsappConversation c) {
        Lead lead = c.getLead();
        return ConversationSummaryResponse.builder()
                .id(c.getId())
                .leadId(lead != null ? lead.getId() : null)
                .leadName(lead != null ? lead.getName() : c.getPhone())
                .phone(c.getPhone())
                .status(c.getStatus())
                .lastMessage(c.getLastMessage())
                .lastMessageAt(c.getLastMessageAt())
                .unreadCount(c.getUnreadCount())
                .windowExpiresAt(c.getWindowExpiresAt())
                .minutesLeftInWindow(minutesLeft(c.getWindowExpiresAt()))
                .assignedToName(c.getAssignedTo() != null
                        ? c.getAssignedTo().getFirstName() + " " + c.getAssignedTo().getLastName() : null)
                .entryPoint(c.getEntryPoint())
                .chatbotActive(c.getChatbotActive())
                .labels(c.getLabels())
                .build();
    }

    private MessageResponse toMessageResponse(WhatsappMessage m) {
        String sentByName = null;
        if (m.getSentBy() != null) {
            sentByName = m.getSentBy().getFirstName() + " " + m.getSentBy().getLastName();
        } else if (Boolean.TRUE.equals(m.getIsBotMessage())) {
            sentByName = "Bot";
        }
        return MessageResponse.builder()
                .id(m.getId())
                .direction(m.getDirection())
                .messageType(m.getMessageType())
                .content(m.getContent())
                .buttonTitle(m.getButtonTitle())
                .status(m.getStatus())
                .isBotMessage(m.getIsBotMessage())
                .sentByName(sentByName)
                .mediaId(m.getMediaId())
                .sentAt(m.getSentAt())
                .build();
    }

    private NoteResponse toNoteResponse(ConversationNote n) {
        String name = n.getCreatedBy() != null
                ? n.getCreatedBy().getFirstName() + " " + n.getCreatedBy().getLastName() : "Unknown";
        return NoteResponse.builder()
                .id(n.getId())
                .content(n.getContent())
                .createdByName(name)
                .createdAt(n.getCreatedAt())
                .build();
    }

    private QuickReplyResponse toQuickReplyResponse(QuickReply qr) {
        return QuickReplyResponse.builder()
                .id(qr.getId())
                .title(qr.getTitle())
                .content(qr.getContent())
                .build();
    }

    // ── Direct send to lead (from lead page) ──────────────────────────────────

    @Transactional
    public void directSend(String phone, String type, String message,
                            String templateName, List<String> params, List<String> paramNames, UUID sentByUserId) {
        String waPhone = normalizeToWaPhone(phone);

        WhatsappConversation conv = conversationRepository.findByPhone(waPhone).orElseGet(() ->
                conversationRepository.save(WhatsappConversation.builder()
                        .phone(waPhone)
                        .entryPoint(ConversationEntryPoint.MANUAL)
                        .chatbotActive(false)
                        .build()));

        if ("TEXT".equals(type)) {
            if (conv.getWindowExpiresAt() == null || conv.getWindowExpiresAt().isBefore(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "24-hour messaging window has expired. Use a template to re-engage this lead.");
            }
        }

        User sentBy = userRepository.findById(sentByUserId).orElse(null);
        String waId;
        String displayText;
        WaMessageType msgType;

        if ("TEMPLATE".equals(type)) {
            log.info("directSend template: name={} params={} paramNames={}", templateName, params, paramNames);
            List<String> pNames = (paramNames != null && !paramNames.isEmpty()) ? paramNames : null;
            waId = whatsAppApiService.sendTemplateMessage(waPhone, templateName, params, pNames, null);
            displayText = "Template: " + templateName;
            msgType = WaMessageType.TEMPLATE;
        } else {
            waId = whatsAppApiService.sendTextMessage(waPhone, message);
            displayText = message;
            msgType = WaMessageType.TEXT;
        }

        messageRepository.save(WhatsappMessage.builder()
                .conversation(conv)
                .whatsappMessageId(waId)
                .direction(MessageDirection.OUTBOUND)
                .messageType(msgType)
                .content(displayText)
                .isBotMessage(false)
                .status(WaMessageStatus.SENT)
                .sentBy(sentBy)
                .sentAt(LocalDateTime.now())
                .build());

        conv.setLastMessage(displayText.length() > 100 ? displayText.substring(0, 100) : displayText);
        conv.setLastMessageAt(LocalDateTime.now());
        conv.setWindowExpiresAt(LocalDateTime.now().plusHours(24));
        conversationRepository.save(conv);

        if (conv.getLead() != null) {
            leadActivityRepository.save(LeadActivity.builder()
                    .lead(conv.getLead())
                    .activityType("TEMPLATE".equals(type)
                            ? ActivityType.WHATSAPP_CAMPAIGN : ActivityType.WHATSAPP_OUTBOUND)
                    .description("TEMPLATE".equals(type)
                            ? "Template sent: " + templateName : "Direct message: " + displayText)
                    .createdBy(sentBy)
                    .build());
        }

        log.info("Direct WhatsApp send: phone={} type={}", waPhone, type);
    }

    private String normalizeToWaPhone(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("[\\s\\-\\(\\)\\+]", "");
        if (phone.length() == 10) return "91" + phone;
        return phone;
    }

    // ── Clear conversation messages ─────────────────────────────────────────────

    @Transactional
    public void clearConversationMessages(UUID conversationId) {
        WhatsappConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        messageRepository.deleteByConversation(conv);
        conv.setLastMessage(null);
        conv.setLastMessageAt(null);
        conv.setUnreadCount(0);
        conv.setChatbotState(com.fmt.fmt_backend.enums.ChatbotState.INITIAL);
        conv.setChatbotActive(true);
        conv.setSavedData(null);
        conv.setStatus(ConversationStatus.OPEN);
        conversationRepository.save(conv);
        log.info("Cleared all messages for conversation {}", conversationId);
    }

    private Long minutesLeft(LocalDateTime windowExpiresAt) {
        if (windowExpiresAt == null) return null;
        long mins = ChronoUnit.MINUTES.between(LocalDateTime.now(), windowExpiresAt);
        return mins > 0 ? mins : 0L;
    }
}
