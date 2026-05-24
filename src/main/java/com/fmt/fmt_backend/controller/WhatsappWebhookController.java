package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.service.ConversationService;
import com.fmt.fmt_backend.service.LeadGenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/webhook/whatsapp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WhatsApp Webhook", description = "Meta WhatsApp Business webhook — public, signature-validated")
public class WhatsappWebhookController {

    @Value("${whatsapp.webhook-verify-token}")
    private String verifyToken;

    @Value("${meta.app-secret}")
    private String appSecret;

    private final ConversationService conversationService;
    private final LeadGenService leadGenService;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // GET — Webhook verification (Meta calls this once when registering)
    // -------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "WhatsApp webhook verification", description = "Called by Meta to verify the webhook URL")
    public ResponseEntity<Object> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("WhatsApp webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }
        log.warn("WhatsApp webhook verification failed: mode={} token={}", mode, token);
        return ResponseEntity.status(403).build();
    }

    // -------------------------------------------------------------------------
    // POST — Incoming events
    // -------------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "WhatsApp webhook receiver",
               description = "Handles incoming messages, status updates, and lead gen events. Returns 200 immediately.")
    public ResponseEntity<Object> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        log.debug("WhatsApp webhook received");

        // Validate signature
        if (signature == null || !validateSignature(rawBody, signature)) {
            log.warn("WhatsApp webhook invalid signature — rejected");
            return ResponseEntity.status(401).build();
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            processPayload(root);
        } catch (Exception e) {
            // Never return 4xx/5xx — Meta retries on failure
            log.error("Error processing WhatsApp webhook: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void processPayload(JsonNode root) {
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                String field = change.path("field").asText();
                JsonNode value = change.path("value");

                if ("leadgen".equals(field)) {
                    handleLeadGen(change);
                    continue;
                }

                // Extract WhatsApp display name from contacts[0].profile.name
                String contactName = null;
                JsonNode contacts = value.path("contacts");
                if (contacts.isArray() && contacts.size() > 0) {
                    String n = contacts.get(0).path("profile").path("name").asText(null);
                    if (n != null && !n.isBlank()) contactName = n.trim();
                }

                // Messages and statuses are under value.messages / value.statuses
                for (JsonNode message : value.path("messages")) {
                    handleIncomingMessage(message, contactName);
                }
                for (JsonNode status : value.path("statuses")) {
                    handleStatusUpdate(status);
                }
            }
        }
    }

    private void handleIncomingMessage(JsonNode message, String contactName) {
        try {
            String from          = message.path("from").asText();
            String type          = message.path("type").asText();
            String whatsappMsgId = message.path("id").asText();
            long   timestamp     = message.path("timestamp").asLong(0);

            String content     = null;
            String buttonId    = null;
            String buttonTitle = null;
            String mediaId     = null;

            switch (type) {
                case "text" -> content = message.path("text").path("body").asText();
                case "button" -> {
                    buttonId    = message.path("button").path("payload").asText();
                    buttonTitle = message.path("button").path("text").asText();
                    content     = buttonTitle;
                }
                case "interactive" -> {
                    JsonNode interactive = message.path("interactive");
                    String interactiveType = interactive.path("type").asText();
                    if ("button_reply".equals(interactiveType)) {
                        buttonId    = interactive.path("button_reply").path("id").asText();
                        buttonTitle = interactive.path("button_reply").path("title").asText();
                        content     = buttonTitle;
                    } else if ("list_reply".equals(interactiveType)) {
                        buttonId    = interactive.path("list_reply").path("id").asText();
                        buttonTitle = interactive.path("list_reply").path("title").asText();
                        content     = buttonTitle;
                    }
                }
                case "image" -> {
                    mediaId = message.path("image").path("id").asText(null);
                    String caption = message.path("image").path("caption").asText(null);
                    content = caption != null && !caption.isBlank() ? "[Image: " + caption + "]" : "[Image]";
                }
                case "document" -> {
                    mediaId = message.path("document").path("id").asText(null);
                    String filename = message.path("document").path("filename").asText(null);
                    content = filename != null ? "[Document: " + filename + "]" : "[Document]";
                }
                case "audio" -> {
                    mediaId = message.path("audio").path("id").asText(null);
                    content = "[Voice Note]";
                }
                case "video" -> {
                    mediaId = message.path("video").path("id").asText(null);
                    content = "[Video]";
                }
                case "sticker" -> {
                    mediaId = message.path("sticker").path("id").asText(null);
                    content = "[Sticker]";
                }
                default -> content = "[" + type + " message]";
            }

            log.info("Incoming WhatsApp message: from={} type={}", from, type);
            conversationService.handleIncomingMessage(from, type, content, buttonId, buttonTitle,
                    mediaId, whatsappMsgId, timestamp > 0 ? timestamp : null, contactName);

        } catch (Exception e) {
            log.error("Error handling incoming message: {}", e.getMessage(), e);
        }
    }

    private void handleStatusUpdate(JsonNode status) {
        try {
            String messageId = status.path("id").asText();
            String statusStr = status.path("status").asText();
            String recipient = status.path("recipient_id").asText();
            conversationService.updateMessageStatus(messageId, statusStr, recipient);
        } catch (Exception e) {
            log.error("Error handling status update: {}", e.getMessage(), e);
        }
    }

    private void handleLeadGen(JsonNode change) {
        try {
            JsonNode value     = change.path("value");
            String  leadgenId  = value.path("leadgen_id").asText();
            String  formId     = value.path("form_id").asText();
            String  adName     = value.path("ad_name").asText(null);
            String  pageId     = value.path("page_id").asText(null);
            log.info("Lead gen event: leadgenId={} formId={}", leadgenId, formId);
            leadGenService.processNewLead(leadgenId, formId, adName, pageId);
        } catch (Exception e) {
            log.error("Error handling lead gen event: {}", e.getMessage(), e);
        }
    }

    private boolean validateSignature(String body, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return expected.equals(signature);
        } catch (Exception e) {
            log.error("Signature validation error: {}", e.getMessage());
            return false;
        }
    }
}
