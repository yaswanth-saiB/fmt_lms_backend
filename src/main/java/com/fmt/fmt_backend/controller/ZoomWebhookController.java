package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.entity.Recording;
import com.fmt.fmt_backend.service.RecordingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Receives webhook events from Zoom.
 *
 * Security: every request is validated with HMAC-SHA256 signature before processing.
 * Zoom retries if we don't respond within 3 seconds — we always return 200 immediately
 * and do all heavy work in the async background job.
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Zoom and Bunny.net webhook receivers — public endpoints, signature-validated")
public class ZoomWebhookController {

    private final RecordingService recordingService;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/webhook/zoom
     *
     * Handles two Zoom event types:
     *  - endpoint.url_validation  (Zoom calls this once when you register the webhook URL)
     *  - recording.completed      (fires when a cloud recording is ready)
     *
     * Must return 200 quickly — Zoom marks the endpoint as failed and retries if no response.
     */
    @PostMapping("/zoom")
    @Operation(summary = "Zoom webhook receiver",
               description = "Called by Zoom for recording.completed events. Signature-validated. Returns 200 immediately.")
    public ResponseEntity<Object> handleZoomWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-zm-signature", required = false) String signature,
            @RequestHeader(value = "x-zm-request-timestamp", required = false) String timestamp) {

        log.debug("Zoom webhook received");

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();

            // ---- Zoom URL validation (one-time handshake when registering the webhook) ----
            if ("endpoint.url_validation".equals(event)) {
                return handleUrlValidation(root);
            }

            // ---- All other events: validate signature first ----
            if (signature == null || timestamp == null) {
                log.warn("Zoom webhook missing signature headers — rejected");
                return ResponseEntity.status(401).build();
            }

            if (!recordingService.validateZoomSignature(rawBody, signature, timestamp)) {
                log.warn("Zoom webhook signature invalid — rejected");
                return ResponseEntity.status(401).build();
            }

            // ---- Route by event type ----
            if ("recording.completed".equals(event)) {
                handleRecordingCompleted(root);
            } else {
                log.debug("Zoom webhook: unhandled event type '{}' — ignored", event);
            }

        } catch (Exception e) {
            // Never return 4xx/5xx to Zoom — it will retry endlessly. Log and return 200.
            log.error("Error processing Zoom webhook: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // Private handlers
    // -------------------------------------------------------------------------

    /**
     * Zoom URL validation handshake — required when first registering the webhook URL.
     * Zoom sends a plainToken, we must echo it as an HMAC-SHA256 hash.
     */
    private ResponseEntity<Object> handleUrlValidation(JsonNode root) {
        try {
            String plainToken = root.path("payload").path("plainToken").asText();
            log.info("Zoom webhook URL validation request received");

            // Zoom expects: { "plainToken": "...", "encryptedToken": "<hmac-sha256>" }
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    System.getenv("ZOOM_WEBHOOK_SECRET") != null
                            ? System.getenv("ZOOM_WEBHOOK_SECRET").getBytes(java.nio.charset.StandardCharsets.UTF_8)
                            : "".getBytes(),
                    "HmacSHA256"));
            byte[] hash = mac.doFinal(plainToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String encryptedToken = java.util.HexFormat.of().formatHex(hash);

            return ResponseEntity.ok(java.util.Map.of(
                    "plainToken", plainToken,
                    "encryptedToken", encryptedToken));
        } catch (Exception e) {
            log.error("URL validation failed: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }
    }

    /**
     * recording.completed — save recording row and kick off async processing.
     *
     * Zoom payload structure:
     * {
     *   "event": "recording.completed",
     *   "payload": {
     *     "object": {
     *       "id": "zoomMeetingId",
     *       "topic": "Class Name",
     *       "duration": 120,
     *       "recording_files": [
     *         { "file_type": "MP4", "download_url": "...", "status": "completed" }
     *       ]
     *     }
     *   }
     * }
     */
    private void handleRecordingCompleted(JsonNode root) {
        JsonNode object = root.path("payload").path("object");

        String zoomMeetingId = object.path("id").asText();
        String topic         = object.path("topic").asText();
        int    durationMins  = object.path("duration").asInt(0);

        // Find the MP4 download URL from recording_files
        String downloadUrl = null;
        for (JsonNode file : object.path("recording_files")) {
            if ("MP4".equalsIgnoreCase(file.path("file_type").asText())
                    && "completed".equalsIgnoreCase(file.path("status").asText())) {
                downloadUrl = file.path("download_url").asText();
                break;
            }
        }

        if (downloadUrl == null || downloadUrl.isBlank()) {
            log.warn("recording.completed for meeting={}: no MP4 file found — ignored", zoomMeetingId);
            return;
        }

        log.info("recording.completed: meeting={}, topic='{}', duration={}min", zoomMeetingId, topic, durationMins);

        // Save recording row synchronously (commits before async starts)
        Recording saved = recordingService.createRecordingFromZoomEvent(
                zoomMeetingId, downloadUrl, topic, durationMins);

        if (saved == null) {
            // Either duplicate or meeting not found — already logged in service
            return;
        }

        // Trigger async download → Bunny upload in background
        // Zoom won't wait for this — we've already committed the DB row above
        recordingService.processRecordingAsync(saved.getId());

        log.info("Recording {} queued for async processing", saved.getId());
    }
}
