package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.entity.Recording;
import com.fmt.fmt_backend.enums.RecordingStatus;
import com.fmt.fmt_backend.repository.MeetingRepository;
import com.fmt.fmt_backend.repository.RecordingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Receives webhook events from Zoom (called by Zoom, not the frontend).
 * This endpoint is public — no auth token required.
 *
 * Zoom sends events like:
 *   - recording.completed → save zoom_download_url, trigger async upload to Cloudflare
 *   - meeting.started     → update meeting status to LIVE
 *   - meeting.ended       → update meeting status to ENDED
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhook", description = "Zoom webhook receiver (called by Zoom, not the app)")
public class WebhookController {

    private final MeetingRepository meetingRepository;
    private final RecordingRepository recordingRepository;

    @PostMapping("/zoom")
    @Operation(summary = "Zoom webhook endpoint — receives meeting and recording events")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, String>> handleZoomWebhook(
            @RequestBody Map<String, Object> payload) {

        String event = (String) payload.get("event");
        log.info("Zoom webhook received: event={}", event);

        if (event == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing event"));
        }

        switch (event) {
            case "meeting.started" -> handleMeetingStarted(payload);
            case "meeting.ended"   -> handleMeetingEnded(payload);
            case "recording.completed" -> handleRecordingCompleted(payload);
            // Zoom URL validation challenge (required when registering webhook)
            case "endpoint.url_validation" -> {
                Object plainToken = ((Map<?, ?>) payload.get("payload")).get("plainToken");
                log.info("Zoom URL validation challenge received");
                return ResponseEntity.ok(Map.of("plainToken", String.valueOf(plainToken)));
            }
            default -> log.debug("Unhandled Zoom event: {}", event);
        }

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ---------------------------------------------------------------
    // Event handlers
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void handleMeetingStarted(Map<String, Object> payload) {
        try {
            Map<String, Object> object = (Map<String, Object>) payload.get("payload");
            if (object == null) return;
            Map<String, Object> meetingObj = (Map<String, Object>) object.get("object");
            if (meetingObj == null) return;
            String zoomMeetingId = String.valueOf(meetingObj.get("id"));

            meetingRepository.findByZoomMeetingId(zoomMeetingId).ifPresent(meeting -> {
                meeting.setStatus(com.fmt.fmt_backend.enums.MeetingStatus.LIVE);
                meeting.setStartedAt(java.time.LocalDateTime.now());
                meetingRepository.save(meeting);
                log.info("Meeting {} marked as LIVE", zoomMeetingId);
            });
        } catch (Exception e) {
            log.error("Error handling meeting.started: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMeetingEnded(Map<String, Object> payload) {
        try {
            Map<String, Object> object = (Map<String, Object>) payload.get("payload");
            if (object == null) return;
            Map<String, Object> meetingObj = (Map<String, Object>) object.get("object");
            if (meetingObj == null) return;
            String zoomMeetingId = String.valueOf(meetingObj.get("id"));

            meetingRepository.findByZoomMeetingId(zoomMeetingId).ifPresent(meeting -> {
                meeting.setStatus(com.fmt.fmt_backend.enums.MeetingStatus.ENDED);
                meeting.setEndedAt(java.time.LocalDateTime.now());
                meetingRepository.save(meeting);
                log.info("Meeting {} marked as ENDED", zoomMeetingId);
            });
        } catch (Exception e) {
            log.error("Error handling meeting.ended: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRecordingCompleted(Map<String, Object> payload) {
        try {
            Map<String, Object> object = (Map<String, Object>) payload.get("payload");
            if (object == null) return;
            Map<String, Object> meetingObj = (Map<String, Object>) object.get("object");
            if (meetingObj == null) return;

            String zoomMeetingId = String.valueOf(meetingObj.get("id"));
            String topic = (String) meetingObj.getOrDefault("topic", "Recording");

            // Get download URL from recording_files array
            Object recordingFiles = meetingObj.get("recording_files");
            String downloadUrl = null;
            if (recordingFiles instanceof java.util.List<?> files && !files.isEmpty()) {
                Object firstFile = files.get(0);
                if (firstFile instanceof Map<?, ?> fileMap) {
                    downloadUrl = (String) fileMap.get("download_url");
                }
            }

            if (downloadUrl == null) {
                log.warn("No download_url in recording.completed for meeting {}", zoomMeetingId);
                return;
            }

            Optional<Meeting> meetingOpt = meetingRepository.findByZoomMeetingId(zoomMeetingId);
            if (meetingOpt.isEmpty()) {
                log.warn("Meeting not found for zoomMeetingId={}", zoomMeetingId);
                return;
            }

            Meeting meeting = meetingOpt.get();

            Recording recording = Recording.builder()
                    .meeting(meeting)
                    .batch(meeting.getBatch())
                    .zoomDownloadUrl(downloadUrl)
                    .title(topic)
                    .status(RecordingStatus.PROCESSING)
                    .build();

            recordingRepository.save(recording);
            log.info("Recording saved for meeting {} — status=PROCESSING (Cloudflare upload pending)", zoomMeetingId);

            // TODO Phase 2: Trigger async Cloudflare upload job here
            // cloudflareUploadService.uploadAsync(recording.getId());

        } catch (Exception e) {
            log.error("Error handling recording.completed: {}", e.getMessage());
        }
    }
}
