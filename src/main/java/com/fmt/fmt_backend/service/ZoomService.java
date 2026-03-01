package com.fmt.fmt_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoomService {

    @Value("${zoom.client-id}")
    private String clientId;  // ✅ Consistent with YAML

    @Value("${zoom.client-secret}")
    private String clientSecret;  // ✅ Consistent with YAML

    @Value("${zoom.account-id}")
    private String accountId;  // ✅ Consistent with YAML

    @Value("${zoom.base-url}")
    private String baseUrl;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private LocalDateTime tokenExpiry;

    /**
     * Get OAuth access token for Zoom API
     */
    private Mono<String> getAccessToken() {
        // Return cached token if still valid
        if (accessToken != null && tokenExpiry != null &&
                tokenExpiry.isAfter(LocalDateTime.now())) {
            return Mono.just(accessToken);
        }

        String credentials = clientId + ":" + clientSecret;  // ✅ Using clientId and clientSecret
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes());

        return webClientBuilder.build()
                .post()
                .uri("https://zoom.us/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue("grant_type=account_credentials&account_id=" + accountId)  // ✅ Using accountId
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    accessToken = response.get("access_token").asText();
                    int expiresIn = response.get("expires_in").asInt();
                    tokenExpiry = LocalDateTime.now().plusSeconds(expiresIn - 60);
                    log.info("✅ Zoom access token obtained");
                    return accessToken;
                })
                .doOnError(error ->
                        log.error("❌ Failed to get Zoom access token: {}", error.getMessage()))
                .onErrorReturn(null);
    }

    /**
     * Create a Zoom meeting
     */
    public Mono<JsonNode> createMeeting(String topic, LocalDateTime startTime,
                                        int durationMinutes, String mentorEmail) {
        return getAccessToken()
                .flatMap(token -> {
                    if (token == null) {
                        return Mono.error(new RuntimeException("No access token"));
                    }

                    ObjectNode meetingDetails = objectMapper.createObjectNode();
                    meetingDetails.put("topic", topic);
                    meetingDetails.put("type", 2);
                    meetingDetails.put("start_time", startTime.format(
                            DateTimeFormatter.ISO_DATE_TIME));
                    meetingDetails.put("duration", durationMinutes);
                    meetingDetails.put("timezone", "Asia/Kolkata");

                    ObjectNode settings = objectMapper.createObjectNode();
                    settings.put("host_video", true);
                    settings.put("participant_video", true);
                    settings.put("join_before_host", false);
                    settings.put("mute_upon_entry", true);
                    settings.put("waiting_room", true);
                    settings.put("audio", "both");
                    meetingDetails.set("settings", settings);

                    return webClientBuilder.build()
                            .post()
                            .uri(baseUrl + "/users/" + mentorEmail + "/meetings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(meetingDetails)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .doOnSuccess(response ->
                                    log.info("✅ Zoom meeting created: {}", topic))
                            .doOnError(error ->
                                    log.error("❌ Failed to create meeting: {}", error.getMessage()));
                });
    }

    /**
     * Get meeting details
     */
    public Mono<JsonNode> getMeeting(String meetingId) {
        return getAccessToken()
                .flatMap(token ->
                        webClientBuilder.build()
                                .get()
                                .uri(baseUrl + "/meetings/" + meetingId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .retrieve()
                                .bodyToMono(JsonNode.class)
                );
    }

    /**
     * Delete a meeting
     */
    public Mono<Boolean> deleteMeeting(String meetingId) {
        return getAccessToken()
                .flatMap(token ->
                        webClientBuilder.build()
                                .delete()
                                .uri(baseUrl + "/meetings/" + meetingId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .retrieve()
                                .toBodilessEntity()
                                .map(response -> response.getStatusCode().is2xxSuccessful())
                )
                .onErrorReturn(false);
    }

    /**
     * Synchronous wrapper for controllers
     */
    public JsonNode createMeetingSync(String topic, LocalDateTime startTime,
                                      int durationMinutes, String mentorEmail) {
        return createMeeting(topic, startTime, durationMinutes, mentorEmail)
                .timeout(Duration.ofSeconds(30))
                .block();
    }
}