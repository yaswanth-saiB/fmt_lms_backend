package com.fmt.fmt_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all Zoom Server-to-Server OAuth API calls.
 *
 * Token lifecycle: Zoom access tokens expire in 1 hour.
 * We cache it and refresh 5 minutes before expiry.
 */
@Service
@Slf4j
public class ZoomService {

    @Value("${zoom.account-id}")
    private String accountId;

    @Value("${zoom.client-id}")
    private String clientId;

    @Value("${zoom.client-secret}")
    private String clientSecret;

    private static final String ZOOM_TOKEN_URL = "https://zoom.us/oauth/token";
    private static final String ZOOM_API_BASE  = "https://api.zoom.us/v2";

    // Simple in-memory token cache
    private String cachedAccessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    private final RestTemplate restTemplate = new RestTemplate();

    // ---------------------------------------------------------------
    // Token Management
    // ---------------------------------------------------------------

    /**
     * Returns a valid Zoom access token.
     * Refreshes automatically if expired or about to expire.
     */
    public synchronized String getAccessToken() {
        if (cachedAccessToken == null || Instant.now().isAfter(tokenExpiresAt.minusSeconds(300))) {
            refreshToken();
        }
        return cachedAccessToken;
    }

    @SuppressWarnings("unchecked")
    private void refreshToken() {
        log.info("Refreshing Zoom access token");

        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + credentials);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String url = ZOOM_TOKEN_URL + "?grant_type=account_credentials&account_id=" + accountId;

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(headers), Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            cachedAccessToken = (String) body.get("access_token");
            int expiresIn = (int) body.getOrDefault("expires_in", 3600);
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
            log.info("Zoom token refreshed, expires in {}s", expiresIn);
        } else {
            throw new RuntimeException("Failed to obtain Zoom access token");
        }
    }

    // ---------------------------------------------------------------
    // Meeting Management
    // ---------------------------------------------------------------

    /**
     * Creates a Zoom meeting and returns id, start_url, join_url.
     *
     * @param topic     Meeting topic/title
     * @param duration  Duration in minutes
     * @return Map with keys: id, start_url, join_url
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> createMeeting(String topic, int duration) {
        log.info("Creating Zoom meeting: topic='{}', duration={}min", topic, duration);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("topic", topic);
        body.put("type", 1);           // Instant meeting (no fixed time)
        body.put("duration", duration);

        Map<String, Object> settings = new HashMap<>();
        settings.put("auto_recording", "cloud");
        settings.put("waiting_room", true);          // students wait until host starts — prevents host-claim prompt
        settings.put("join_before_host", false);     // no one enters before mentor starts
        settings.put("mute_upon_entry", true);       // students join muted
        settings.put("participant_video", false);    // students join with camera off
        settings.put("host_video", true);            // mentor joins with camera on
        settings.put("allow_multiple_devices", false);
        body.put("settings", settings);

        ResponseEntity<Map> response = restTemplate.exchange(
                ZOOM_API_BASE + "/users/me/meetings",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to create Zoom meeting");
        }

        Map<String, Object> result = response.getBody();
        Map<String, String> meeting = new HashMap<>();
        meeting.put("id", String.valueOf(result.get("id")));
        meeting.put("start_url", (String) result.get("start_url"));
        meeting.put("join_url", (String) result.get("join_url"));

        log.info("Zoom meeting created: id={}", meeting.get("id"));
        return meeting;
    }

    /**
     * Fetches the MP4 recording download URL for a past Zoom meeting.
     *
     * Calls GET /meetings/{zoomMeetingId}/recordings and extracts the first
     * completed MP4 file. Appends the download_token as ?access_token= so
     * the returned URL can be used directly without Bearer auth (same approach
     * as the Zoom webhook flow).
     *
     * @param zoomMeetingId Zoom's numeric meeting ID (e.g. "87654321234")
     * @return Full download URL with access token appended
     * @throws RuntimeException if no completed MP4 recording exists
     */
    @SuppressWarnings("unchecked")
    public String fetchRecordingDownloadUrl(String zoomMeetingId) {
        log.info("Fetching recording info from Zoom API for meeting {}", zoomMeetingId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Map> response = restTemplate.exchange(
                ZOOM_API_BASE + "/meetings/" + zoomMeetingId + "/recordings",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Zoom API returned no data for meeting " + zoomMeetingId);
        }

        Map<String, Object> body = response.getBody();
        String downloadToken = (String) body.getOrDefault("download_token", null);

        List<Map<String, Object>> files = (List<Map<String, Object>>) body.get("recording_files");
        if (files == null || files.isEmpty()) {
            throw new RuntimeException("No recording files found on Zoom for meeting " + zoomMeetingId);
        }

        for (Map<String, Object> file : files) {
            String fileType = (String) file.getOrDefault("file_type", "");
            String status   = (String) file.getOrDefault("status", "");
            if ("MP4".equalsIgnoreCase(fileType) && "completed".equalsIgnoreCase(status)) {
                String downloadUrl = (String) file.get("download_url");
                if (downloadToken != null && !downloadToken.isBlank()) {
                    downloadUrl = downloadUrl + "?access_token=" + downloadToken;
                } else {
                    log.warn("No download_token for Zoom meeting {} — download may fail", zoomMeetingId);
                }
                log.info("Found MP4 recording for Zoom meeting {}", zoomMeetingId);
                return downloadUrl;
            }
        }

        throw new RuntimeException("No completed MP4 recording found on Zoom for meeting " + zoomMeetingId
                + ". It may still be processing — wait a few minutes and try again.");
    }

    /**
     * Gets the current status of a Zoom meeting.
     *
     * @param zoomMeetingId The Zoom meeting ID
     * @return "waiting", "started", or "ended"
     */
    @SuppressWarnings("unchecked")
    public String getMeetingStatus(String zoomMeetingId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ZOOM_API_BASE + "/meetings/" + zoomMeetingId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object statusObj = response.getBody().get("status");
                return statusObj != null ? statusObj.toString() : "unknown";
            }
        } catch (Exception e) {
            log.warn("Could not fetch Zoom meeting status for {}: {}", zoomMeetingId, e.getMessage());
        }
        return "unknown";
    }
}
