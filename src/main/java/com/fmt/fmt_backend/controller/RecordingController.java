package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.PlayUrlResponse;
import com.fmt.fmt_backend.dto.StudentRecordingResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.RecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Student recording endpoints.
 *
 * Students can list available recordings for a batch they are enrolled in,
 * and fetch a signed, time-limited Bunny.net play URL to watch a recording.
 *
 * Security:
 * - Student must be enrolled in the batch to list or play recordings.
 * - bunnyVideoId and zoomDownloadUrl are NEVER included in any response.
 * - Play URLs expire after 3 hours — frontend must call /play fresh each time.
 */
@RestController
@RequestMapping("/api/recordings")
@RequiredArgsConstructor
@Tag(name = "Recordings", description = "Student recording access — list and play class recordings")
@SecurityRequirement(name = "bearerAuth")
public class RecordingController {

    private final RecordingService recordingService;
    private final AuthService authService;

    /**
     * List all available recordings for a batch.
     *
     * Only AVAILABLE recordings are returned — PROCESSING, FAILED, EXPIRED are filtered server-side.
     * Returns 403 if the student is not enrolled in the batch.
     */
    @GetMapping("/batch/{batchId}")
    @Operation(summary = "List available recordings for a batch",
               description = "Returns recordings the student can watch. Only AVAILABLE recordings are included. " +
                             "Returns 403 if not enrolled, 404 if batch not found.")
    public ResponseEntity<ApiResponse<List<StudentRecordingResponse>>> getBatchRecordings(
            @PathVariable UUID batchId) {
        User student = currentStudent();
        List<StudentRecordingResponse> recordings =
                recordingService.getAvailableRecordingsForBatch(batchId, student);
        return ResponseEntity.ok(ApiResponse.success("Recordings fetched", recordings));
    }

    /**
     * Get a signed, time-limited play URL for a recording.
     *
     * The URL is valid for 3 hours. Frontend must call this fresh each time the student
     * opens a video — do NOT cache the URL in localStorage or sessionStorage.
     *
     * Returns 400 if recording is still PROCESSING, 403 if expired or not enrolled.
     */
    @GetMapping("/{recordingId}/play")
    @Operation(summary = "Get signed play URL for a recording",
               description = "Returns a Bunny.net signed URL valid for 3 hours (expiresIn=10800). " +
                             "Call this fresh each time a student opens a video.")
    public ResponseEntity<ApiResponse<PlayUrlResponse>> getPlayUrl(
            @PathVariable UUID recordingId) {
        User student = currentStudent();
        PlayUrlResponse response = recordingService.generatePlayUrl(recordingId, student);
        return ResponseEntity.ok(ApiResponse.success("Play URL generated", response));
    }

    private User currentStudent() {
        return authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));
    }
}
