package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.*;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignLeadSenderService {

    private final WhatsappCampaignRepository campaignRepository;
    private final WhatsappConversationRepository conversationRepository;
    private final WhatsappMessageRepository messageRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final WhatsappCampaignRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final WhatsAppApiService whatsAppApiService;

    /**
     * Sends a campaign message to one lead in its own committed transaction.
     * Using REQUIRES_NEW so each lead commits immediately — Meta delivery webhooks
     * can find the conversation/message as soon as this method returns.
     * Entities are loaded fresh inside this transaction to avoid detached-entity errors.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WhatsappCampaignRecipient sendToLead(
            UUID campaignId, UUID leadId,
            List<String> resolvedParams, List<String> paramNames,
            String headerImageHandle, UUID sentByUserId) {

        WhatsappCampaign campaign = campaignRepository.findById(campaignId).orElseThrow();
        Lead lead = leadRepository.findById(leadId).orElseThrow();
        User sentBy = sentByUserId != null ? userRepository.findById(sentByUserId).orElse(null) : null;

        WhatsappCampaignRecipient.WhatsappCampaignRecipientBuilder rb =
                WhatsappCampaignRecipient.builder()
                        .campaign(campaign)
                        .lead(lead)
                        .phone(lead.getPhone())
                        .leadName(lead.getName());
        try {
            String msgId = whatsAppApiService.sendTemplateMessage(
                    lead.getPhone(), campaign.getTemplateName(), resolvedParams,
                    (paramNames == null || paramNames.isEmpty()) ? null : paramNames,
                    headerImageHandle);

            WhatsappConversation conv = getOrCreateConversation(lead);
            WhatsappMessage msg = WhatsappMessage.builder()
                    .conversation(conv)
                    .whatsappMessageId(msgId)
                    .direction(MessageDirection.OUTBOUND)
                    .messageType(WaMessageType.TEMPLATE)
                    .content("Campaign: " + campaign.getTemplateName())
                    .templateName(campaign.getTemplateName())
                    .isBotMessage(false)
                    .status(WaMessageStatus.SENT)
                    .sentBy(sentBy)
                    .sentAt(LocalDateTime.now())
                    .build();
            messageRepository.save(msg);

            conv.setLastMessage("Campaign: " + campaign.getTemplateName());
            conv.setLastMessageAt(LocalDateTime.now());
            conv.setChatbotActive(true);
            conv.setChatbotState(ChatbotState.MENU_SHOWN);
            conv.setStatus(ConversationStatus.OPEN);
            conversationRepository.save(conv);

            leadActivityRepository.save(LeadActivity.builder()
                    .lead(lead)
                    .activityType(ActivityType.WHATSAPP_CAMPAIGN)
                    .description("Campaign sent: " + campaign.getName())
                    .createdBy(sentBy)
                    .build());

            if (lead.getStatus() == LeadStatus.IMPORTED) {
                lead.setStatus(LeadStatus.CAMPAIGN_SENT);
                leadRepository.save(lead);
            }

            rb.waMessageId(msgId).failed(false).sentAt(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Campaign send failed for lead {}: {}", leadId, e.getMessage());
            rb.failed(true).errorMessage(truncate(e.getMessage(), 490))
              .deliveryStatus(WaMessageStatus.FAILED);
        }

        return recipientRepository.save(rb.build());
    }

    private WhatsappConversation getOrCreateConversation(Lead lead) {
        String phone12 = normalizePhone(lead.getPhone());
        String phoneRaw = lead.getPhone() != null ? lead.getPhone().replaceAll("[\\s\\-\\(\\)\\+]", "") : null;

        WhatsappConversation conv = conversationRepository.findByPhone(phone12)
                .or(() -> phoneRaw != null && !phoneRaw.equals(phone12)
                        ? conversationRepository.findByPhone(phoneRaw)
                        : Optional.empty())
                .orElseGet(() -> conversationRepository.save(WhatsappConversation.builder()
                        .phone(phone12)
                        .lead(lead)
                        .entryPoint(ConversationEntryPoint.CAMPAIGN)
                        .chatbotActive(false)
                        .status(ConversationStatus.OPEN)
                        .build()));

        if (conv.getLead() == null) {
            conv.setLead(lead);
        }
        return conv;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("[\\s\\-\\(\\)\\+]", "");
        if (phone.length() == 10) return "91" + phone;
        return phone;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
