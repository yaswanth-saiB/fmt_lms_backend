package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/**
 * Admin-only request to manually trigger a recording for a class that was
 * conducted when the app was down (Zoom webhook was missed).
 *
 * The backend calls the Zoom API to fetch the recording download URL — admin
 * does NOT need to paste any token-bearing URLs from Zoom.
 *
 * Lookup priority:
 *   1. meetingId (our DB UUID) — most precise, use if admin can find it in the meetings list
 *   2. zoomMeetingId only — backend looks up meeting in our DB by zoom meeting ID
 *   3. zoomMeetingId + batchId — meeting was not in our DB (class run fully outside app);
 *      backend creates a placeholder meeting record linked to the given batch
 */
@Data
public class ManualRecordingRequest {

    /**
     * The Zoom meeting ID — the numeric string visible in Zoom dashboard
     * (e.g. "87654321234"). Used to call the Zoom API to fetch the recording.
     * REQUIRED.
     */
    @NotBlank(message = "zoomMeetingId is required — find it in your Zoom dashboard or cloud recordings page")
    private String zoomMeetingId;

    /**
     * Our internal meeting UUID. Optional but preferred when known —
     * avoids an ambiguous lookup by zoomMeetingId.
     */
    private UUID meetingId;

    /**
     * Our internal batch UUID. Required ONLY if the meeting is not in our DB
     * (class was run directly from Zoom without going through the app).
     * A placeholder meeting record will be created and linked to this batch.
     */
    private UUID batchId;

    /** Optional — overrides the title stored in the meeting record. */
    private String title;

    /** Optional — overrides the duration stored in the meeting record. */
    private Integer durationMins;
}
