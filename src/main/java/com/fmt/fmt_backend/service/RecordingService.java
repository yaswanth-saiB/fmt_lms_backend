package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.PlayUrlResponse;
import com.fmt.fmt_backend.dto.RecordingResponse;
import com.fmt.fmt_backend.dto.StudentRecordingResponse;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.entity.Recording;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.RecordingStatus;
import com.fmt.fmt_backend.repository.BatchEnrollmentRepository;
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
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
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
    private final BatchRepository batchRepository;
    private final BatchEnrollmentRepository batchEnrollmentRepository;
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
     *
     * Re-fetches the recording with JOIN FETCH on meeting to avoid LazyInitializationException.
     * The entity passed in from processRecordingAsync is detached (its transaction already closed),
     * so accessing lazy associations on it would fail without this re-fetch.
     *
     * Note: @Transactional is intentionally NOT on this method — it is called via this.processRecording()
     * (same-bean self-invocation), which bypasses the Spring AOP proxy. Each repository.save() call
     * uses its own transaction, which is sufficient here.
     */
    protected void processRecording(Recording recording) {
        // Re-fetch with meeting eagerly loaded — entity from caller is detached
        Recording r = recordingRepository.findByIdWithMeeting(recording.getId())
                .orElseThrow(() -> new RuntimeException("Recording not found: " + recording.getId()));

        String zoomDownloadUrl  = r.getZoomDownloadUrl();
        String title            = r.getTitle();
        UUID   recordingId      = r.getId();
        String zoomMeetingId    = r.getMeeting().getZoomMeetingId();

        // ---- Step 1: Create video object in Bunny ----
        String bunnyVideoId = createBunnyVideoObject(title);
        log.info("Bunny video object created: videoId={} for recording={}", bunnyVideoId, recordingId);

        // ---- Step 2: Download from Zoom and stream-upload to Bunny ----
        streamZoomToBunny(zoomDownloadUrl, bunnyVideoId);
        log.info("Video streamed to Bunny for recording={}", recordingId);

        // ---- Step 3: Update DB with Bunny video ID (status stays PROCESSING — Bunny webhook sets AVAILABLE) ----
        r.setBunnyVideoId(bunnyVideoId);
        recordingRepository.save(r);

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
    // STUDENT — list available recordings for a batch
    // =========================================================================

    /**
     * Returns available recordings for a batch, verifying the student is enrolled.
     *
     * Only AVAILABLE and non-expired recordings are returned — status is filtered server-side.
     * The frontend receives a simplified response with no bunnyVideoId or zoomDownloadUrl.
     */
    @Transactional(readOnly = true)
    public List<StudentRecordingResponse> getAvailableRecordingsForBatch(UUID batchId, User student) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Batch not found"));

        boolean enrolled = batchEnrollmentRepository.existsByBatchAndStudentAndIsActiveTrue(batch, student);
        if (!enrolled) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "You are not enrolled in this batch");
        }

        return recordingRepository.findByBatchAndStatusOrderByCreatedAtDesc(batch, RecordingStatus.AVAILABLE)
                .stream()
                .filter(r -> r.getExpiresAt() == null || r.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(r -> StudentRecordingResponse.builder()
                        .id(r.getId())
                        .title(r.getTitle())
                        .recordedDate(r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : null)
                        .durationMins(r.getDurationMins())
                        .status(r.getStatus())
                        .build())
                .toList();
    }

    // =========================================================================
    // STUDENT — get signed play URL
    // =========================================================================

    /**
     * Generates a signed, time-limited Bunny.net play URL for a specific recording.
     *
     * Verifies the student is enrolled in the recording's batch.
     * Returns 400 if recording is still PROCESSING, 403 if expired or not enrolled.
     */
    @Transactional(readOnly = true)
    public PlayUrlResponse generatePlayUrl(UUID recordingId, User student) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Recording not found"));

        // Enrollment check
        boolean enrolled = batchEnrollmentRepository
                .existsByBatchAndStudentAndIsActiveTrue(recording.getBatch(), student);
        if (!enrolled) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "You are not enrolled in this batch");
        }

        // Status checks
        if (recording.getStatus() == RecordingStatus.PROCESSING ||
                recording.getStatus() == RecordingStatus.FAILED) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Recording is not yet available (still processing)");
        }
        if (recording.getStatus() == RecordingStatus.EXPIRED) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Recording has expired");
        }
        if (recording.getExpiresAt() != null && recording.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Recording has expired");
        }

        if (recording.getBunnyVideoId() == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Recording video ID is missing — contact support");
        }

        String playUrl = buildSignedBunnyUrl(recording.getBunnyVideoId());
        return PlayUrlResponse.builder()
                .playUrl(playUrl)
                .expiresIn(10800)
                .build();
    }

    // =========================================================================
    // MENTOR — list recordings
    // =========================================================================

    public List<RecordingResponse> getMentorRecordings(UUID mentorId) {
        return recordingRepository.findByMentorId(mentorId)
                .stream()
                .map(this::toMentorRecordingResponse)
                .toList();
    }

    public List<RecordingResponse> getMentorBatchRecordings(UUID batchId, UUID mentorId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Batch not found"));

        return recordingRepository.findByBatchOrderByCreatedAtDesc(batch)
                .stream()
                .map(this::toMentorRecordingResponse)
                .toList();
    }

    private RecordingResponse toMentorRecordingResponse(Recording r) {
        String courseName = null;
        try {
            courseName = r.getBatch().getCourse() != null
                    ? r.getBatch().getCourse().getTitle() : null;
        } catch (Exception ignored) { /* lazy load may fail — safe to skip */ }

        return RecordingResponse.builder()
                .id(r.getId())
                .title(r.getTitle())
                .batchId(r.getBatch().getId())
                .batchName(r.getBatch().getName())
                .courseName(courseName)
                .status(r.getStatus())
                .durationMins(r.getDurationMins())
                .recordedDate(r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : null)
                .createdAt(r.getCreatedAt())
                .build();
    }

    // =========================================================================
    // Bunny signed URL generation (Token Authentication)
    // =========================================================================

    /**
     * Generates a signed Bunny.net Stream play URL valid for 3 hours.
     *
     * Algorithm:
     *   message = bunnyVideoId + expiryTimestamp
     *   token   = Base64URL(HMAC-SHA256(key=bunnyTokenKey, message))
     *   url     = https://{cdn-hostname}/{bunnyVideoId}/play?token={token}&expires={expiry}
     */
    private String buildSignedBunnyUrl(String bunnyVideoId) {
        try {
            long expires = Instant.now().plusSeconds(10800).getEpochSecond();

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(bunnyTokenKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((bunnyVideoId + expires).getBytes(StandardCharsets.UTF_8));
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

            return "https://" + bunnyCdnHostname + "/" + bunnyVideoId + "/play"
                    + "?token=" + token + "&expires=" + expires;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signed play URL", e);
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
