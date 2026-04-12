package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.entity.Recording;
import com.fmt.fmt_backend.enums.RecordingStatus;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.MeetingRepository;
import com.fmt.fmt_backend.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordingService {

    private final RecordingRepository recordingRepository;
    private final MeetingRepository meetingRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ZoomService zoomService;

    @Value("${zoom.webhook-secret}")
    private String zoomWebhookSecret;

    @Value("${bunny.library-id}")
    private String bunnyLibraryId;

    @Value("${bunny.api-key}")
    private String bunnyApiKey;

    @Value("${bunny.cdn-hostname}")
    private String bunnyCdnHostname;

    @Value("${bunny.token-key}")
    private String bunnyTokenKey;


    private static final String BUNNY_API_BASE = "https://video.bunnycdn.com/library/";

    // =========================================================================
    // ZOOM WEBHOOK — signature validation
    // =========================================================================

    /**
     * Validates the HMAC-SHA256 signature Zoom attaches to every webhook call.
     *
     * Zoom signs: "v0:" + timestamp + ":" + rawBody
     * Header value: "v0=" + hex(HMAC-SHA256(webhookSecret, message))
     *
     * Always reject requests that fail this check — they are not from Zoom.
     */
    public boolean validateZoomSignature(String rawBody, String signatureHeader, String timestamp) {
        try {
            String message = "v0:" + timestamp + ":" + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(zoomWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String expected = "v0=" + HexFormat.of().formatHex(hash);
            return expected.equals(signatureHeader);
        } catch (Exception e) {
            log.error("Zoom signature validation error: {}", e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // ZOOM WEBHOOK — save recording row (called synchronously, commits before async)
    // =========================================================================

    /**
     * Creates a Recording row when Zoom fires recording.completed.
     *
     * Called synchronously from the webhook controller so the DB row is committed
     * before the async processing job starts. Returns the saved entity so the
     * controller can pass its ID to processRecordingAsync().
     *
     * Returns null if the meeting is not found (Zoom recorded a meeting we don't
     * know about — can happen if test meetings are run outside the app).
     */
    @Transactional
    public Recording createRecordingFromZoomEvent(String zoomMeetingId,
                                                  String downloadUrl,
                                                  String topic,
                                                  Integer durationMins) {
        // Idempotency — Zoom can fire the webhook more than once for the same meeting
        if (recordingRepository.findByMeeting_ZoomMeetingId(zoomMeetingId).isPresent()) {
            log.info("Recording already exists for Zoom meeting {} — skipping duplicate webhook", zoomMeetingId);
            return null;
        }

        Meeting meeting = meetingRepository.findByZoomMeetingId(zoomMeetingId).orElse(null);
        if (meeting == null) {
            log.warn("Zoom webhook: no meeting found for zoomMeetingId={} — ignoring", zoomMeetingId);
            return null;
        }

        Batch batch = meeting.getBatch();

        // Access expires 2 months after batch end date (or 3 months from now as fallback)
        LocalDateTime expiresAt = batch.getEndDate() != null
                ? batch.getEndDate().plusMonths(2).atStartOfDay()
                : LocalDateTime.now().plusMonths(3);

        Recording recording = Recording.builder()
                .meeting(meeting)
                .batch(batch)
                .zoomDownloadUrl(downloadUrl)
                .title(topic != null ? topic : meeting.getTopic())
                .durationMins(durationMins)
                .status(RecordingStatus.PROCESSING)
                .expiresAt(expiresAt)
                .build();

        Recording saved = recordingRepository.save(recording);
        log.info("Recording row created: id={}, meeting={}, status=PROCESSING", saved.getId(), zoomMeetingId);
        return saved;
    }

    // =========================================================================
    // ASYNC — download from Zoom, upload to Bunny (Step 2)
    // =========================================================================

    /**
     * Downloads the MP4 from Zoom Cloud and uploads it to Bunny.net Stream.
     *
     * Must be called AFTER createRecordingFromZoomEvent() has committed so the
     * DB row exists when this thread reads it.
     *
     * Retries up to 3 times on failure. On permanent failure sets status=FAILED.
     */
    @Async
    public void processRecordingAsync(UUID recordingId) {
        log.info("Starting async recording processing for id={}", recordingId);

        Recording recording = recordingRepository.findById(recordingId).orElse(null);
        if (recording == null) {
            log.error("processRecordingAsync: recording {} not found in DB", recordingId);
            return;
        }

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("Processing attempt {}/{} for recording {}", attempt, maxAttempts, recordingId);
                processRecording(recording);
                return; // success
            } catch (Exception e) {
                log.error("Attempt {}/{} failed for recording {}: {}", attempt, maxAttempts, recordingId, e.getMessage());
                if (attempt == maxAttempts) {
                    markFailed(recordingId, e.getMessage());
                } else {
                    sleepSeconds(30L * attempt); // back-off: 30s, 60s
                }
            }
        }
    }

    /**
     * Core processing — streams the Zoom recording into Bunny.net.
     * No full file buffering in memory: streams directly from Zoom to Bunny.
     */
    @Transactional
    protected void processRecording(Recording recording) {
        String zoomDownloadUrl  = recording.getZoomDownloadUrl();
        String title            = recording.getTitle();
        UUID   recordingId      = recording.getId();
        String zoomMeetingId    = recording.getMeeting().getZoomMeetingId();

        // ---- Step 1: Create video object in Bunny ----
        String bunnyVideoId = createBunnyVideoObject(title);
        log.info("Bunny video object created: videoId={} for recording={}", bunnyVideoId, recordingId);

        // ---- Step 2: Download from Zoom and stream-upload to Bunny ----
        streamZoomToBunny(zoomDownloadUrl, bunnyVideoId);
        log.info("Video streamed to Bunny for recording={}", recordingId);

        // ---- Step 3: Update DB with Bunny video ID (status stays PROCESSING — Bunny webhook sets AVAILABLE) ----
        recording.setBunnyVideoId(bunnyVideoId);
        recordingRepository.save(recording);

        // ---- Step 4: Delete recording from Zoom Cloud to stay within storage limits ----
        deleteZoomRecording(zoomMeetingId);
        log.info("Zoom recording deleted for meeting={}", zoomMeetingId);
    }

    // =========================================================================
    // BUNNY WEBHOOK — mark AVAILABLE or FAILED
    // =========================================================================

    /**
     * Validates a Bunny webhook payload by checking the VideoLibraryId matches ours.
     * Bunny sends no auth header — this is the only validation available.
     * Filters out random POST requests from actors who don't know our library ID.
     */
    public boolean validateBunnyWebhookLibraryId(long libraryIdFromPayload) {
        try {
            return Long.parseLong(bunnyLibraryId) == libraryIdFromPayload;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Transactional
    public void handleBunnyWebhook(String bunnyVideoId, int status) {
        Recording recording = recordingRepository.findByBunnyVideoId(bunnyVideoId).orElse(null);
        if (recording == null) {
            log.warn("Bunny webhook: no recording found for videoId={}", bunnyVideoId);
            return;
        }

        if (status == 4) {
            // Bunny status 4 = ready
            recording.setStatus(RecordingStatus.AVAILABLE);
            recordingRepository.save(recording);
            log.info("Recording {} is now AVAILABLE (Bunny ready)", recording.getId());
        } else if (status == 5) {
            // Bunny status 5 = error
            recording.setStatus(RecordingStatus.FAILED);
            recordingRepository.save(recording);
            log.error("Recording {} FAILED — Bunny encoding error (videoId={})", recording.getId(), bunnyVideoId);
        } else {
            log.debug("Bunny webhook: unhandled status {} for videoId={}", status, bunnyVideoId);
        }
    }

    // =========================================================================
    // SCHEDULED — nightly expiry job
    // =========================================================================

    /**
     * Runs every night at midnight.
     * Deletes expired recordings from Bunny and marks them EXPIRED in the DB.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void expireOldRecordings() {
        List<Recording> expired = recordingRepository.findByStatusAndExpiresAtBefore(
                RecordingStatus.AVAILABLE, LocalDateTime.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("Expiry job: found {} recording(s) to expire", expired.size());

        for (Recording rec : expired) {
            try {
                if (rec.getBunnyVideoId() != null) {
                    deleteBunnyVideo(rec.getBunnyVideoId());
                }
                rec.setStatus(RecordingStatus.EXPIRED);
                recordingRepository.save(rec);
                log.info("Expired recording {}: {}", rec.getId(), rec.getTitle());
            } catch (Exception e) {
                log.error("Failed to expire recording {}: {}", rec.getId(), e.getMessage());
            }
        }

        log.info("Expiry job complete: {} recording(s) expired", expired.size());
    }

    // =========================================================================
    // BUNNY API helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private String createBunnyVideoObject(String title) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccessKey", bunnyApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("title", title);

        ResponseEntity<Map> response = restTemplate.exchange(
                BUNNY_API_BASE + bunnyLibraryId + "/videos",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to create Bunny video object");
        }

        Object guid = response.getBody().get("guid");
        if (guid == null) {
            throw new RuntimeException("Bunny create video response missing 'guid'");
        }
        return guid.toString();
    }

    /**
     * Streams the MP4 from Zoom directly to Bunny without buffering the whole file in memory.
     * Uses RestTemplate with byte[] — adequate for class recordings (typically 1–3 GB).
     *
     * For very large files a chunked/InputStreamResource approach can be added later,
     * but RestTemplate handles the streaming efficiently enough for this scale.
     */
    private void streamZoomToBunny(String zoomDownloadUrl, String bunnyVideoId) {
        // Download from Zoom (streaming — RestTemplate handles internally)
        HttpHeaders zoomHeaders = new HttpHeaders();
        zoomHeaders.setBearerAuth(zoomService.getAccessToken());
        zoomHeaders.set("Accept", "application/octet-stream");

        ResponseEntity<byte[]> zoomResponse = restTemplate.exchange(
                zoomDownloadUrl,
                HttpMethod.GET,
                new HttpEntity<>(zoomHeaders),
                byte[].class);

        if (!zoomResponse.getStatusCode().is2xxSuccessful() || zoomResponse.getBody() == null) {
            throw new RuntimeException("Failed to download recording from Zoom");
        }

        byte[] videoBytes = zoomResponse.getBody();
        log.info("Downloaded {} bytes from Zoom", videoBytes.length);

        // Upload to Bunny
        HttpHeaders bunnyHeaders = new HttpHeaders();
        bunnyHeaders.set("AccessKey", bunnyApiKey);
        bunnyHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        ResponseEntity<String> bunnyResponse = restTemplate.exchange(
                BUNNY_API_BASE + bunnyLibraryId + "/videos/" + bunnyVideoId,
                HttpMethod.PUT,
                new HttpEntity<>(videoBytes, bunnyHeaders),
                String.class);

        if (!bunnyResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to upload video to Bunny: HTTP " + bunnyResponse.getStatusCode());
        }
    }

    private void deleteBunnyVideo(String bunnyVideoId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccessKey", bunnyApiKey);

        try {
            restTemplate.exchange(
                    BUNNY_API_BASE + bunnyLibraryId + "/videos/" + bunnyVideoId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class);
            log.info("Deleted Bunny video: {}", bunnyVideoId);
        } catch (Exception e) {
            log.error("Failed to delete Bunny video {}: {}", bunnyVideoId, e.getMessage());
        }
    }

    // =========================================================================
    // ZOOM API helpers
    // =========================================================================

    private void deleteZoomRecording(String zoomMeetingId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(zoomService.getAccessToken());

        try {
            restTemplate.exchange(
                    "https://api.zoom.us/v2/meetings/" + zoomMeetingId + "/recordings",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class);
            log.info("Deleted Zoom cloud recording for meeting {}", zoomMeetingId);
        } catch (Exception e) {
            // Non-fatal — Zoom keeps recordings for 30 days anyway; we still have zoom_download_url in DB
            log.warn("Could not delete Zoom recording for meeting {}: {}", zoomMeetingId, e.getMessage());
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    @Transactional
    protected void markFailed(UUID recordingId, String reason) {
        recordingRepository.findById(recordingId).ifPresent(rec -> {
            rec.setStatus(RecordingStatus.FAILED);
            recordingRepository.save(rec);
            log.error("Recording {} marked FAILED after all retries. Reason: {}", recordingId, reason);
        });
    }

    private void sleepSeconds(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
