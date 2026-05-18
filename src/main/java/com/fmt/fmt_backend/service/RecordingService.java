package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.ManualRecordingRequest;
import com.fmt.fmt_backend.dto.RegisterExternalRecordingRequest;
import com.fmt.fmt_backend.dto.UpdateRecordingRequest;
import com.fmt.fmt_backend.entity.BatchEnrollment;
import com.fmt.fmt_backend.dto.PlayUrlResponse;
import com.fmt.fmt_backend.dto.RecordingResponse;
import com.fmt.fmt_backend.dto.StudentRecordingResponse;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.entity.Recording;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.MeetingStatus;
import com.fmt.fmt_backend.enums.RecordingStatus;
import com.fmt.fmt_backend.repository.BatchEnrollmentRepository;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.MeetingRepository;
import com.fmt.fmt_backend.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final ResendEmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

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
     * Creates one Recording row per batch when Zoom fires recording.completed.
     *
     * A meeting can be linked to multiple batches — each batch gets its own Recording
     * row so students in each batch can access the recording independently.
     * All sibling recordings share the same zoomDownloadUrl; only the first one is
     * actually downloaded and uploaded to Bunny (processRecordingAsync copies the
     * resulting bunnyVideoId to the others after upload completes).
     *
     * Returns an empty list if the meeting is not found or was already processed.
     */
    @Transactional
    public List<Recording> createRecordingFromZoomEvent(String zoomMeetingId,
                                                        String downloadUrl,
                                                        String topic,
                                                        Integer durationMins) {
        // Idempotency — Zoom can fire the webhook more than once for the same meeting
        if (recordingRepository.existsByMeeting_ZoomMeetingId(zoomMeetingId)) {
            log.info("Recording already exists for Zoom meeting {} — skipping duplicate webhook", zoomMeetingId);
            return List.of();
        }

        Meeting meeting = meetingRepository.findByZoomMeetingId(zoomMeetingId).orElse(null);
        if (meeting == null) {
            log.warn("Zoom webhook: no meeting found for zoomMeetingId={} — ignoring", zoomMeetingId);
            return List.of();
        }

        String resolvedTitle = topic != null ? topic : meeting.getTopic();
        List<Recording> saved = new ArrayList<>();

        for (Batch batch : meeting.getBatches()) {
            LocalDateTime expiresAt = batch.getEndDate() != null
                    ? batch.getEndDate().plusMonths(2).atStartOfDay()
                    : LocalDateTime.now().plusMonths(3);

            Recording recording = Recording.builder()
                    .meeting(meeting)
                    .batch(batch)
                    .zoomDownloadUrl(downloadUrl)
                    .title(resolvedTitle)
                    .durationMins(durationMins)
                    .status(RecordingStatus.PROCESSING)
                    .expiresAt(expiresAt)
                    .build();

            saved.add(recordingRepository.save(recording));
        }

        log.info("Recording rows created: count={}, meeting={}, status=PROCESSING", saved.size(), zoomMeetingId);
        return saved;
    }

    // =========================================================================
    // ADMIN MANUAL — create recording when Zoom webhook was missed (app was down)
    // =========================================================================

    /**
     * Admin-only: manually trigger recording processing for a class that ran
     * while the app was down (Zoom webhook was never received).
     *
     * Lookup order for the meeting:
     *   1. request.meetingId (our UUID) — direct lookup, most reliable
     *   2. request.zoomMeetingId field on the Meeting entity
     *   3. If still not found and request.batchId is provided → create placeholder meeting
     *
     * The Zoom download URL is fetched fresh from the Zoom API using the
     * zoomMeetingId — admin never needs to paste token-bearing URLs.
     *
     * Returns a RecordingResponse (batch name already resolved inside the transaction)
     * so the caller can kick off processRecordingAsync() without touching lazy fields.
     */
    @Transactional
    public RecordingResponse createManualRecording(ManualRecordingRequest request) {
        String zoomMeetingId = request.getZoomMeetingId().trim();

        // Step 1 — resolve Meeting record
        Meeting meeting = null;
        if (request.getMeetingId() != null) {
            meeting = meetingRepository.findById(request.getMeetingId())
                    .orElseThrow(() -> new ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND,
                            "Meeting not found in our system for id=" + request.getMeetingId()));
        }
        if (meeting == null) {
            meeting = meetingRepository.findByZoomMeetingId(zoomMeetingId).orElse(null);
        }
        if (meeting == null) {
            // Class was run fully outside the app — create a placeholder meeting
            if (request.getBatchId() == null) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "No meeting found for Zoom meeting ID '" + zoomMeetingId
                        + "' in our system. If this class was run directly from Zoom (not through the app), "
                        + "provide batchId so a placeholder meeting can be created.");
            }
            Batch batch = batchRepository.findById(request.getBatchId())
                    .orElseThrow(() -> new ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Batch not found"));
            String placeholderTitle = request.getTitle() != null
                    ? request.getTitle() : "Manual Recording — Zoom " + zoomMeetingId;
            java.util.Set<Batch> placeholderBatches = new java.util.HashSet<>();
            placeholderBatches.add(batch);
            meeting = Meeting.builder()
                    .batches(placeholderBatches)
                    .mentor(batch.getCourse().getMentor())
                    .zoomMeetingId(zoomMeetingId)
                    .topic(placeholderTitle)
                    .status(MeetingStatus.ENDED)
                    .durationMins(request.getDurationMins() != null ? request.getDurationMins() : 120)
                    .build();
            meeting = meetingRepository.save(meeting);
            log.info("Created placeholder meeting record for Zoom meeting {} in batch {}", zoomMeetingId, request.getBatchId());
        }

        // Step 2 — idempotency: don't create a second recording for the same meeting
        if (recordingRepository.existsByMeeting_ZoomMeetingId(zoomMeetingId)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "A recording already exists for Zoom meeting " + zoomMeetingId
                    + ". If it failed, use the retry endpoint instead.");
        }

        // Step 3 — fetch fresh download URL from Zoom API (includes a valid token)
        String downloadUrl = zoomService.fetchRecordingDownloadUrl(zoomMeetingId);

        // Step 4 — build and save one recording row per batch
        // For a placeholder meeting the batches set contains exactly the one batch admin specified.
        // For a real meeting the batches set matches all batches the class was created for.
        String title = request.getTitle() != null ? request.getTitle() : meeting.getTopic();
        Integer durationMins = request.getDurationMins() != null
                ? request.getDurationMins() : meeting.getDurationMins();

        // Use the first batch for the response (primary batch)
        Batch batch = meeting.getBatches().iterator().next();
        LocalDateTime expiresAt = batch.getEndDate() != null
                ? batch.getEndDate().plusMonths(2).atStartOfDay()
                : LocalDateTime.now().plusMonths(3);

        Recording primaryRecording = Recording.builder()
                .meeting(meeting)
                .batch(batch)
                .zoomDownloadUrl(downloadUrl)
                .title(title)
                .durationMins(durationMins)
                .status(RecordingStatus.PROCESSING)
                .expiresAt(expiresAt)
                .build();

        Recording saved = recordingRepository.save(primaryRecording);
        log.info("Manual recording created: id={}, meeting={}, status=PROCESSING", saved.getId(), zoomMeetingId);

        // Create sibling recordings for any additional batches on this meeting
        for (Batch siblingBatch : meeting.getBatches()) {
            if (siblingBatch.getId().equals(batch.getId())) continue;
            LocalDateTime siblingExpiresAt = siblingBatch.getEndDate() != null
                    ? siblingBatch.getEndDate().plusMonths(2).atStartOfDay()
                    : LocalDateTime.now().plusMonths(3);
            Recording sibling = Recording.builder()
                    .meeting(meeting)
                    .batch(siblingBatch)
                    .zoomDownloadUrl(downloadUrl)
                    .title(title)
                    .durationMins(durationMins)
                    .status(RecordingStatus.PROCESSING)
                    .expiresAt(siblingExpiresAt)
                    .build();
            recordingRepository.save(sibling);
            log.info("Manual recording sibling created for batch {}", siblingBatch.getId());
        }

        // Resolve batch/course names while still inside the transaction (lazy-safe)
        String courseName = null;
        try { courseName = batch.getCourse() != null ? batch.getCourse().getTitle() : null; } catch (Exception ignored) {}

        return RecordingResponse.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .batchId(batch.getId())
                .batchName(batch.getName())
                .courseName(courseName)
                .status(saved.getStatus())
                .durationMins(saved.getDurationMins())
                .build();
    }

    /**
     * Admin-only: retry a recording that is in FAILED status.
     *
     * Re-fetches a fresh Zoom download URL (the stored one may have an expired token)
     * and re-triggers async processing. Resets status to PROCESSING so the admin
     * can track progress.
     *
     * Only FAILED recordings can be retried — PROCESSING or AVAILABLE recordings
     * are rejected to avoid duplicate uploads.
     */
    @Transactional
    public void retryFailedRecording(UUID recordingId) {
        Recording recording = recordingRepository.findByIdWithMeeting(recordingId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Recording not found"));

        if (recording.getStatus() != RecordingStatus.FAILED) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Only FAILED recordings can be retried. Current status: " + recording.getStatus());
        }

        if (recording.getMeeting() == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "This is an externally uploaded recording — it has no Zoom meeting and cannot be retried");
        }
        String zoomMeetingId = recording.getMeeting().getZoomMeetingId();
        if (zoomMeetingId == null || zoomMeetingId.isBlank()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "This recording has no Zoom meeting ID — cannot re-fetch download URL from Zoom");
        }

        // Fetch a fresh download URL with a valid token
        String freshDownloadUrl = zoomService.fetchRecordingDownloadUrl(zoomMeetingId);
        recording.setZoomDownloadUrl(freshDownloadUrl);
        recording.setStatus(RecordingStatus.PROCESSING);
        recordingRepository.save(recording);

        log.info("Recording {} reset to PROCESSING for retry (Zoom meeting {})", recordingId, zoomMeetingId);
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
        String zoomMeetingId    = r.getMeeting() != null ? r.getMeeting().getZoomMeetingId() : null;

        // ---- Step 1: Create video object in Bunny (only once — reuse on retries) ----
        // If a previous attempt already created the video object, reuse that videoId
        // instead of creating another orphan in Bunny.
        String bunnyVideoId = r.getBunnyVideoId();
        UUID meetingId = r.getMeeting() != null ? r.getMeeting().getId() : null;

        if (bunnyVideoId == null) {
            // Get or create the Bunny collection for this batch (lazy — created on first recording)
            // Use getId() on the proxy — safe, Hibernate always has the ID without initializing
            UUID batchId = r.getBatch().getId();
            String collectionId = null;
            try {
                collectionId = getOrCreateBatchCollection(batchId);
            } catch (Exception e) {
                log.warn("Could not get/create Bunny collection for batch {} — video will be uncollected: {}",
                        batchId, e.getMessage());
            }
            bunnyVideoId = createBunnyVideoObject(title, collectionId);
            r.setBunnyVideoId(bunnyVideoId);
            recordingRepository.save(r); // persist early so retries pick up the same videoId
            log.info("Bunny video object created: videoId={} collectionId={} for recording={}",
                    bunnyVideoId, collectionId, recordingId);

            // Copy bunnyVideoId to sibling recordings (same meeting, other batches) so they all
            // share the same Bunny video — the Bunny webhook will mark all of them AVAILABLE at once.
            if (meetingId != null) {
                String finalBunnyVideoId = bunnyVideoId;
                recordingRepository.findByMeeting_IdAndBunnyVideoIdIsNull(meetingId)
                        .forEach(sibling -> {
                            sibling.setBunnyVideoId(finalBunnyVideoId);
                            recordingRepository.save(sibling);
                            log.info("Copied bunnyVideoId={} to sibling recording {} (batch {})",
                                    finalBunnyVideoId, sibling.getId(), sibling.getBatch().getId());
                        });
            }
        } else {
            log.info("Reusing existing Bunny videoId={} for recording={} (retry)", bunnyVideoId, recordingId);
        }

        // ---- Step 2: Download from Zoom to temp file, then upload to Bunny ----
        streamZoomToBunny(zoomDownloadUrl, bunnyVideoId, recordingId);
        log.info("Video uploaded to Bunny for recording={}", recordingId);

        // Zoom deletion is deferred to handleBunnyWebhook (status=4) — only delete after
        // Bunny confirms encoding is successful. If Bunny fails, admin needs to retry
        // which re-fetches from Zoom, so the Zoom copy must still be there.
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
        // A Bunny video is shared across all batch recordings for the same meeting —
        // fetch all of them so every batch's recording is updated in one webhook.
        List<Recording> recordings = recordingRepository.findAllByBunnyVideoId(bunnyVideoId);
        if (recordings.isEmpty()) {
            log.warn("Bunny webhook: no recordings found for videoId={}", bunnyVideoId);
            return;
        }

        if (status == 4) {
            // Bunny status 4 = ready — mark every batch recording AVAILABLE
            boolean anyUpdated = false;
            for (Recording recording : recordings) {
                if (recording.getStatus() == RecordingStatus.AVAILABLE) {
                    log.info("Recording {} already AVAILABLE — skipping", recording.getId());
                    continue;
                }
                recording.setStatus(RecordingStatus.AVAILABLE);
                recordingRepository.save(recording);
                log.info("Recording {} is now AVAILABLE (Bunny ready, batch {})",
                        recording.getId(), recording.getBatch().getId());
                notifyRecordingAvailable(recording);
                anyUpdated = true;
            }
            if (anyUpdated) {
                // Delete from Zoom once — all recordings share the same meeting
                Recording first = recordings.get(0);
                String zoomMeetingId = first.getMeeting() != null
                        ? first.getMeeting().getZoomMeetingId() : null;
                if (zoomMeetingId != null) {
                    deleteZoomRecording(zoomMeetingId);
                }
            }
        } else if (status == 5) {
            // Bunny status 5 = error — mark all FAILED
            for (Recording recording : recordings) {
                recording.setStatus(RecordingStatus.FAILED);
                recordingRepository.save(recording);
                log.error("Recording {} FAILED — Bunny encoding error (videoId={})", recording.getId(), bunnyVideoId);
            }
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
    private String createBunnyVideoObject(String title, String collectionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccessKey", bunnyApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new java.util.HashMap<>();
        body.put("title", title);
        if (collectionId != null && !collectionId.isBlank()) {
            body.put("collectionId", collectionId);
        }

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
     * Returns the Bunny collection ID for this batch, creating one if it doesn't exist yet.
     *
     * Collection name format: "{courseName} - {batchName}"
     * e.g. "Technical Analysis - Batch 1004"
     *
     * The collectionId is stored on the Batch entity so subsequent recordings
     * in the same batch reuse the same collection without extra API calls.
     *
     * Takes batchId (not Batch) because this is called from an @Async thread where
     * the Batch passed from processRecording is a detached lazy proxy with no session.
     * Using findByIdWithCourse does a JOIN FETCH so course.getTitle() works in memory
     * after the repo transaction closes — each batchRepository call opens its own transaction.
     */
    @SuppressWarnings("unchecked")
    private String getOrCreateBatchCollection(UUID batchId) {
        // Re-fetch batch with course eagerly loaded — avoids LazyInitializationException
        Batch batch = batchRepository.findByIdWithCourse(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        if (batch.getBunnyCollectionId() != null && !batch.getBunnyCollectionId().isBlank()) {
            return batch.getBunnyCollectionId();
        }

        // Build collection name
        String courseName = batch.getCourse() != null ? batch.getCourse().getTitle() : null;
        String collectionName = (courseName != null ? courseName + " - " : "") + batch.getName();

        HttpHeaders headers = new HttpHeaders();
        headers.set("AccessKey", bunnyApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("name", collectionName);

        ResponseEntity<Map> response = restTemplate.exchange(
                BUNNY_API_BASE + bunnyLibraryId + "/collections",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.warn("Failed to create Bunny collection for batch {} — videos will be uncollected", batch.getId());
            return null;
        }

        Object guid = response.getBody().get("guid");
        if (guid == null) {
            log.warn("Bunny collection response missing 'guid' for batch {}", batch.getId());
            return null;
        }

        String collectionId = guid.toString();
        batch.setBunnyCollectionId(collectionId);
        batchRepository.save(batch);
        log.info("Created Bunny collection '{}' (id={}) for batch {}", collectionName, collectionId, batch.getId());
        return collectionId;
    }

    /**
     * Downloads the MP4 from Zoom to a temp file, then uploads it to Bunny.net Stream.
     *
     * Temp file approach avoids loading the entire video into the JVM heap (which would
     * cause OOM on EC2 with -Xmx512m for recordings longer than a few minutes).
     *
     * The download URL must already contain ?access_token=<download_token> — appended
     * by the webhook handler. Bearer OAuth does not work for Zoom recording downloads.
     *
     * Temp file is always deleted in the finally block, even if upload fails.
     */
    private void streamZoomToBunny(String zoomDownloadUrl, String bunnyVideoId, UUID recordingId) {
        Path tempFile = Path.of(System.getProperty("java.io.tmpdir"), "recording-" + recordingId + ".mp4");

        try {
            // ---- Download from Zoom → temp file (streams to disk, no heap buffering) ----
            log.info("Downloading Zoom recording {} to temp file: {}", recordingId, tempFile);

            restTemplate.execute(
                    zoomDownloadUrl,
                    HttpMethod.GET,
                    request -> request.getHeaders().set("Accept", "application/octet-stream"),
                    response -> {
                        Files.copy(response.getBody(), tempFile, StandardCopyOption.REPLACE_EXISTING);
                        return null;
                    });

            long fileSize = Files.size(tempFile);
            log.info("Zoom download complete: {} bytes for recording {}", fileSize, recordingId);

            // ---- Upload temp file → Bunny with correct Content-Length ----
            log.info("Uploading recording {} to Bunny (videoId={})", recordingId, bunnyVideoId);

            HttpHeaders bunnyHeaders = new HttpHeaders();
            bunnyHeaders.set("AccessKey", bunnyApiKey);
            bunnyHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            bunnyHeaders.setContentLength(fileSize);

            ResponseEntity<String> bunnyResponse = restTemplate.exchange(
                    BUNNY_API_BASE + bunnyLibraryId + "/videos/" + bunnyVideoId,
                    HttpMethod.PUT,
                    new HttpEntity<>(new FileSystemResource(tempFile), bunnyHeaders),
                    String.class);

            if (!bunnyResponse.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Bunny upload failed: HTTP " + bunnyResponse.getStatusCode());
            }

            log.info("Bunny upload complete for recording {}", recordingId);

        } catch (IOException e) {
            throw new RuntimeException("I/O error during recording transfer: " + e.getMessage(), e);
        } finally {
            // Always clean up — even if download or upload threw
            try {
                if (Files.deleteIfExists(tempFile)) {
                    log.info("Temp file deleted: {}", tempFile);
                }
            } catch (IOException e) {
                log.warn("Could not delete temp file {}: {}", tempFile, e.getMessage());
            }
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
    // MENTOR — get signed play URL (own classes only)
    // =========================================================================

    /**
     * Generates a signed Bunny.net play URL for the mentor.
     *
     * Verifies the recording belongs to a class conducted by this mentor.
     * Mentor can watch AVAILABLE recordings for any of their classes — no
     * batch enrollment check (they're the teacher, not a student).
     */
    @Transactional(readOnly = true)
    public PlayUrlResponse generateMentorPlayUrl(UUID recordingId, UUID mentorId) {
        Recording recording = recordingRepository.findByIdWithMeeting(recordingId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Recording not found"));

        // Ownership — only the mentor who conducted the class can watch it
        // External recordings (no meeting) skip ownership check — accessible to any mentor
        if (recording.getMeeting() != null) {
            UUID conductingMentorId = recording.getMeeting().getMentor().getId();
            if (!conductingMentorId.equals(mentorId)) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "This recording is not from one of your classes");
            }
        }

        if (recording.getStatus() == RecordingStatus.PROCESSING ||
                recording.getStatus() == RecordingStatus.FAILED) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Recording is not yet available (still processing)");
        }
        if (recording.getStatus() == RecordingStatus.EXPIRED ||
                (recording.getExpiresAt() != null && recording.getExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.GONE, "Recording has expired");
        }
        if (recording.getBunnyVideoId() == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Recording video ID is missing — contact support");
        }

        return PlayUrlResponse.builder()
                .playUrl(buildSignedBunnyUrl(recording.getBunnyVideoId()))
                .expiresIn(10800)
                .build();
    }

    // =========================================================================
    // ADMIN — get signed play URL (any recording)
    // =========================================================================

    /**
     * Generates a signed Bunny.net play URL for admin.
     *
     * No ownership or enrollment check — admin can watch any recording.
     * Still validates that the recording is AVAILABLE (not processing/expired).
     */
    @Transactional(readOnly = true)
    public PlayUrlResponse generateAdminPlayUrl(UUID recordingId) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Recording not found"));

        if (recording.getStatus() == RecordingStatus.PROCESSING ||
                recording.getStatus() == RecordingStatus.FAILED) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Recording is not yet available (status: " + recording.getStatus() + ")");
        }
        if (recording.getStatus() == RecordingStatus.EXPIRED ||
                (recording.getExpiresAt() != null && recording.getExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.GONE, "Recording has expired");
        }
        if (recording.getBunnyVideoId() == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Recording video ID is missing — contact support");
        }

        return PlayUrlResponse.builder()
                .playUrl(buildSignedBunnyUrl(recording.getBunnyVideoId()))
                .expiresIn(10800)
                .build();
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

    // =========================================================================
    // ADMIN — list recordings for a specific batch (all statuses)
    // =========================================================================

    public List<RecordingResponse> getAdminBatchRecordings(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Batch not found"));

        return recordingRepository.findByBatchOrderByCreatedAtDesc(batch)
                .stream()
                .map(this::toMentorRecordingResponse)
                .toList();
    }

    // =========================================================================
    // ADMIN — register an externally uploaded recording (Google Drive → Bunny)
    // =========================================================================

    @org.springframework.transaction.annotation.Transactional
    public RecordingResponse adminRegisterExternalRecording(RegisterExternalRecordingRequest request) {
        Batch batch = batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Batch not found"));

        Recording recording = Recording.builder()
                .meeting(null)
                .batch(batch)
                .title(request.getTitle())
                .bunnyVideoId(request.getBunnyVideoId())
                .durationMins(request.getDurationMins())
                .status(com.fmt.fmt_backend.enums.RecordingStatus.AVAILABLE)
                .build();

        recording = recordingRepository.save(recording);
        log.info("External recording registered — batch: {}, bunnyVideoId: {}, title: {}",
                request.getBatchId(), request.getBunnyVideoId(), request.getTitle());
        return toMentorRecordingResponse(recording);
    }

    // =========================================================================
    // ADMIN — edit recording title / duration
    // =========================================================================

    @org.springframework.transaction.annotation.Transactional
    public RecordingResponse adminUpdateRecording(UUID recordingId, UpdateRecordingRequest request) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Recording not found"));

        recording.setTitle(request.getTitle());
        if (request.getDurationMins() != null) recording.setDurationMins(request.getDurationMins());

        recording = recordingRepository.save(recording);
        log.info("Recording {} title updated to: {}", recordingId, request.getTitle());
        return toMentorRecordingResponse(recording);
    }

    // =========================================================================
    // ADMIN — delete a recording (DB + best-effort Bunny delete)
    // =========================================================================

    @org.springframework.transaction.annotation.Transactional
    public void adminDeleteRecording(UUID recordingId) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Recording not found"));

        String bunnyVideoId = recording.getBunnyVideoId();

        recordingRepository.delete(recording);
        log.info("Recording {} deleted from DB", recordingId);

        // Best-effort Bunny delete — don't fail the whole operation if Bunny API is unavailable
        if (bunnyVideoId != null && !bunnyVideoId.isBlank()) {
            try {
                String url = BUNNY_API_BASE + bunnyLibraryId + "/videos/" + bunnyVideoId;
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("AccessKey", bunnyApiKey);
                org.springframework.http.HttpEntity<Void> entity =
                        new org.springframework.http.HttpEntity<>(headers);
                restTemplate.exchange(url, org.springframework.http.HttpMethod.DELETE, entity, String.class);
                log.info("Recording {} deleted from Bunny (videoId: {})", recordingId, bunnyVideoId);
            } catch (Exception e) {
                log.warn("Could not delete recording {} from Bunny — manual cleanup may be needed. Error: {}",
                        bunnyVideoId, e.getMessage());
            }
        }
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
     * Generates a signed Bunny.net Stream embed URL valid for 3 hours.
     *
     * Bunny Stream token authentication algorithm (from Bunny docs):
     *   token = SHA256(tokenKey + videoId + expiryTimestamp)  →  lowercase hex
     *   url   = https://iframe.mediadelivery.net/embed/{libraryId}/{videoId}?token={token}&expires={expiry}
     *
     * Note: This is plain SHA-256 (NOT HMAC-SHA256) and hex-encoded (NOT Base64URL).
     * The embed URL uses iframe.mediadelivery.net, not the pull-zone CDN hostname.
     */
    private String buildSignedBunnyUrl(String bunnyVideoId) {
        try {
            long expires = Instant.now().plusSeconds(10800).getEpochSecond();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (bunnyTokenKey + bunnyVideoId + expires).getBytes(StandardCharsets.UTF_8));
            String token = HexFormat.of().formatHex(hash);

            return "https://iframe.mediadelivery.net/embed/" + bunnyLibraryId
                    + "/" + bunnyVideoId
                    + "?token=" + token + "&expires=" + expires;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signed play URL", e);
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Sends "recording is ready" emails to all enrolled students and the mentor.
     *
     * Called after Bunny marks the recording AVAILABLE.
     * Each email is fired with @Async in ResendEmailService — non-blocking.
     *
     * The recording URL points to the app's batch recordings page, which requires
     * login. Sharing the link with non-members gives them a login wall — safe.
     */
    private void notifyRecordingAvailable(Recording recording) {
        try {
            Batch batch = recording.getBatch();
            String recordingTitle = recording.getTitle();
            String batchName = batch.getName();
            String recordingUrl = frontendUrl + "/dashboard/recordings?batchId=" + batch.getId();

            List<BatchEnrollment> enrollments = batchEnrollmentRepository.findByBatchAndIsActiveTrue(batch);
            List<String> studentEmails = enrollments.stream()
                    .map(e -> e.getStudent().getEmail())
                    .collect(java.util.stream.Collectors.toList());

            if (recording.getMeeting() != null) {
                // One email: mentor in To (greeted by name), all students in BCC
                com.fmt.fmt_backend.entity.User mentor = recording.getMeeting().getMentor();
                emailService.sendRecordingReadyEmail(
                        mentor.getEmail(),
                        mentor.getFirstName(),
                        studentEmails,
                        recordingTitle,
                        batchName,
                        recordingUrl);
                log.info("Recording ready email queued — mentor: {}, {} student(s) in BCC, batch: {}",
                        mentor.getEmail(), studentEmails.size(), batchName);
            } else {
                // External recording (no meeting/mentor) — send individually to each student
                for (BatchEnrollment enrollment : enrollments) {
                    com.fmt.fmt_backend.entity.User student = enrollment.getStudent();
                    emailService.sendRecordingAvailableEmail(
                            student.getEmail(),
                            student.getFirstName(),
                            recordingTitle,
                            batchName,
                            recordingUrl);
                }
                log.info("Recording available emails queued for {} student(s) (external recording, no mentor), batch: {}",
                        studentEmails.size(), batchName);
            }

        } catch (Exception e) {
            // Non-fatal — recording is already AVAILABLE; email failure must not roll back anything
            log.error("Failed to send recording available notifications for recording {}: {}",
                    recording.getId(), e.getMessage());
        }
    }

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
