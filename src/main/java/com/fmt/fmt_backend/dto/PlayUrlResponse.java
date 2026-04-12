package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Returned by GET /api/recordings/{recordingId}/play
 * The playUrl is a signed, time-limited Bunny.net Stream URL — valid for 3 hours.
 * Frontend must call this endpoint fresh each time a student opens a video.
 */
@Data
@Builder
public class PlayUrlResponse {
    private String playUrl;
    private int expiresIn; // seconds — always 10800 (3 hours)
}
