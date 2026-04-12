package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.service.RecordingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives webhook events from Bunny.net Stream.
 *
 * Bunny sends no auth header — validated by checking VideoLibraryId in the payload.
 *
 * Payload:
 * {
 *   "VideoLibraryId": 635843,
 *   "VideoGuid": "bunny-video-uuid",
 *   "Status": 3
 * }
 *
 * Status values: 0=created, 1=uploaded, 2=processing, 3=transcoding, 4=finished, 5=error
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Zoom and Bunny.net webhook receivers — public endpoints, payload-validated")
public class BunnyWebhookController {

    private final RecordingService recordingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/bunny")
    @Operation(summary = "Bunny.net webhook receiver",
               description = "Called by Bunny.net when video status changes. Sets recording AVAILABLE (status=4) or FAILED (status=5).")
    public ResponseEntity<Void> handleBunnyWebhook(@RequestBody String rawBody) {

        log.debug("Bunny webhook received");

        try {
            JsonNode root     = objectMapper.readTree(rawBody);
            long libraryId    = root.path("VideoLibraryId").asLong(-1);
            String videoGuid  = root.path("VideoGuid").asText();
            int status        = root.path("Status").asInt(-1);

            // Validate the library ID matches ours — Bunny has no auth header
            if (!recordingService.validateBunnyWebhookLibraryId(libraryId)) {
                log.warn("Bunny webhook: VideoLibraryId {} does not match — rejected", libraryId);
                return ResponseEntity.status(401).build();
            }

            if (videoGuid.isBlank()) {
                log.warn("Bunny webhook: missing VideoGuid — ignored");
                return ResponseEntity.ok().build();
            }

            log.info("Bunny webhook: videoId={}, status={}", videoGuid, status);
            recordingService.handleBunnyWebhook(videoGuid, status);

        } catch (Exception e) {
            log.error("Error processing Bunny webhook: {}", e.getMessage(), e);
        }

        // Always return 200 — Bunny retries on non-200
        return ResponseEntity.ok().build();
    }
}
