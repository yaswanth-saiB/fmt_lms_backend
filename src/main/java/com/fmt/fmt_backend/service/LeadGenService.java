package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.*;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadGenService {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v18.0";

    @Value("${whatsapp.access-token}")
    private String accessToken;

    @Value("${resend.admin-email:admin@firstmilliontrade.com}")
    private String adminEmail;

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final WhatsappConversationRepository conversationRepository;
    private final WhatsappMessageRepository messageRepository;
    private final WhatsAppApiService whatsAppApiService;
    private final ResendEmailService resendEmailService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public void processNewLead(String leadgenId, String formId, String adName, String incomingPageId) {
        log.info("Processing Meta lead gen: leadgenId={} formId={} adName={}", leadgenId, formId, adName);

        // 1. Fetch lead details from Meta Graph API
        Map<String, Object> leadData = fetchLeadFromMeta(leadgenId);
        if (leadData == null) {
            log.error("Could not fetch lead data for leadgenId={} — notifying admin", leadgenId);
            resendEmailService.sendAdminEmail(adminEmail,
                    "⚠️ Meta Lead Gen fetch failed",
                    "Could not fetch lead data for leadgenId=" + leadgenId
                    + " (formId=" + formId + ", adName=" + adName + ")."
                    + " Check the WHATSAPP_ACCESS_TOKEN and Meta app permissions.");
            return;
        }

        // 2. Parse field_data — format: [{"name":"full_name","values":["John"]}, ...]
        String name = null, phone = null, email = null, courseInterest = null;
        Object fieldDataObj = leadData.get("field_data");
        if (fieldDataObj instanceof List<?> fieldList) {
            for (Object item : fieldList) {
                if (item instanceof Map<?, ?> field) {
                    String fieldName = String.valueOf(field.get("name")).toLowerCase();
                    Object values = field.get("values");
                    String value = (values instanceof List<?> vl && !vl.isEmpty())
                            ? String.valueOf(vl.get(0)) : null;
                    if (value == null || value.equals("null")) continue;
                    if (fieldName.contains("name"))                              name = value;
                    else if (fieldName.contains("phone") || fieldName.contains("mobile")) phone = value;
                    else if (fieldName.contains("email"))                        email = value;
                    else if (fieldName.contains("course") || fieldName.contains("interest")) courseInterest = value;
                }
            }
        }

        // Normalize phone — strip formatting, ensure country code
        if (phone != null) {
            phone = phone.replaceAll("[\\s\\-\\(\\)\\+]", "");
            if (phone.startsWith("0")) phone = "91" + phone.substring(1);
            if (!phone.startsWith("91") && phone.length() == 10) phone = "91" + phone;
        }

        final String finalPhone = (phone != null && !phone.isBlank()) ? phone : null;
        final String finalName  = name  != null ? name  : "Unknown";
        final String finalEmail = email;
        final String finalCourseInterest = courseInterest;
        final String source = adName != null ? adName : formId;

        // 3. Duplicate check — by phone (if present) or email
        if (finalPhone != null && leadRepository.existsByPhone(finalPhone)) {
            log.info("Lead gen {}: duplicate phone {} — skipping lead creation", leadgenId, finalPhone);
            ensureConversation(finalPhone, null);
            notifyAdmin(finalName, finalPhone, finalEmail, source, true);
            return;
        }

        // 4. Save lead — even if no phone (email-only or name-only lead)
        Lead lead = leadRepository.save(Lead.builder()
                .name(finalName)
                .phone(finalPhone != null ? finalPhone : "META_" + leadgenId) // placeholder if no phone
                .email(finalEmail)
                .courseInterest(finalCourseInterest)
                .source(LeadSource.META_ADS_DIRECT)
                .status(LeadStatus.NEW)
                .build());

        log.info("Meta lead created: id={} name={} phone={} email={} ad='{}'",
                lead.getId(), finalName, finalPhone, finalEmail, source);

        // 5. Log import activity
        leadActivityRepository.save(LeadActivity.builder()
                .lead(lead)
                .activityType(ActivityType.LEAD_IMPORTED)
                .description("Imported via Meta Lead Gen — " + source)
                .build());

        // 6. Notify admin/sales immediately — instant lead alert
        notifyAdmin(finalName, finalPhone, finalEmail, source, false);

        // 7. WhatsApp flow — only possible if we have a real phone number
        if (finalPhone == null) {
            log.info("Lead gen {}: no phone — skipping WhatsApp conversation", leadgenId);
            return;
        }

        // 8. Create WhatsApp conversation
        WhatsappConversation conversation = conversationRepository.findByPhone(finalPhone)
                .orElseGet(() -> conversationRepository.save(WhatsappConversation.builder()
                        .phone(finalPhone)
                        .lead(lead)
                        .entryPoint(ConversationEntryPoint.LEAD_GEN)
                        .chatbotState(ChatbotState.INITIAL_LEAD_GEN)
                        .windowExpiresAt(LocalDateTime.now().plusHours(24))
                        .build()));

        // 9. Send acknowledgment template (non-blocking — fails gracefully if template not approved yet)
        try {
            String waMessageId = whatsAppApiService.sendTemplateMessage(
                    finalPhone, "fmt_lead_acknowledgement", List.of(finalName));

            messageRepository.save(WhatsappMessage.builder()
                    .conversation(conversation)
                    .whatsappMessageId(waMessageId)
                    .direction(MessageDirection.OUTBOUND)
                    .messageType(WaMessageType.TEMPLATE)
                    .content("fmt_lead_acknowledgement sent to " + finalName)
                    .templateName("fmt_lead_acknowledgement")
                    .isBotMessage(true)
                    .status(WaMessageStatus.SENT)
                    .sentAt(LocalDateTime.now())
                    .build());

            conversation.setLastMessage("Welcome template sent");
            conversation.setLastMessageAt(LocalDateTime.now());
            conversationRepository.save(conversation);

            log.info("Acknowledgment template sent to {} ({})", finalPhone, finalName);

        } catch (Exception e) {
            log.warn("Could not send acknowledgment template to {} — template may not be approved yet: {}",
                    finalPhone, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void notifyAdmin(String name, String phone, String email, String adSource, boolean isDuplicate) {
        try {
            String subject = isDuplicate
                    ? "⚠️ Duplicate Meta Lead — " + name
                    : "🔥 New Lead from Meta Ad — " + name;

            String body = String.format("""
                    <h2>%s</h2>
                    <table style="border-collapse:collapse;font-family:sans-serif">
                      <tr><td style="padding:6px 12px;font-weight:bold">Name</td><td style="padding:6px 12px">%s</td></tr>
                      <tr><td style="padding:6px 12px;font-weight:bold">Phone</td><td style="padding:6px 12px">%s</td></tr>
                      <tr><td style="padding:6px 12px;font-weight:bold">Email</td><td style="padding:6px 12px">%s</td></tr>
                      <tr><td style="padding:6px 12px;font-weight:bold">Ad / Form</td><td style="padding:6px 12px">%s</td></tr>
                      <tr><td style="padding:6px 12px;font-weight:bold">Status</td><td style="padding:6px 12px">%s</td></tr>
                    </table>
                    <p style="margin-top:16px;color:#666">Log in to the CRM to follow up.</p>
                    """,
                    isDuplicate ? "Duplicate lead received from Meta" : "New lead received from Meta ad",
                    name,
                    phone != null ? phone : "—",
                    email != null ? email : "—",
                    adSource != null ? adSource : "—",
                    isDuplicate ? "Already exists in CRM" : "Added to CRM — status: NEW"
            );

            resendEmailService.sendAdminEmail(adminEmail, subject, body);
        } catch (Exception e) {
            log.error("Failed to send lead notification email: {}", e.getMessage());
        }
    }

    private void ensureConversation(String phone, Lead lead) {
        conversationRepository.findByPhone(phone).orElseGet(() ->
                conversationRepository.save(WhatsappConversation.builder()
                        .phone(phone)
                        .lead(lead)
                        .entryPoint(ConversationEntryPoint.LEAD_GEN)
                        .chatbotState(ChatbotState.INITIAL_LEAD_GEN)
                        .build()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchLeadFromMeta(String leadgenId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    GRAPH_API_BASE + "/" + leadgenId + "?fields=field_data,created_time",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Meta Graph API lead fetch failed for {}: {}", leadgenId, e.getMessage());
            return null;
        }
    }
}
