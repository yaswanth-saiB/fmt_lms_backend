package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.CreateMeetingRequest;
import com.fmt.fmt_backend.dto.MeetingDTO;
import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Meetings", description = "Zoom meeting management APIs")
public class MeetingController {

    private final MeetingService meetingService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Create a Zoom meeting",
            description = "Mentor creates a Zoom meeting for a batch. Returns join URLs for students and start URL for mentor.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Meeting created successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not authorized for this batch"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Zoom API error")
    })
    public ResponseEntity<ApiResponse<MeetingDTO>> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        MeetingDTO meeting = meetingService.createMeeting(currentUser.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Meeting created successfully", meeting));
    }

    @GetMapping("/student/upcoming")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Get student's upcoming meetings",
            description = "Get all upcoming meetings for batches the student is enrolled in",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<MeetingDTO>>> getStudentUpcomingMeetings() {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<MeetingDTO> meetings = meetingService.getStudentUpcomingMeetings(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Upcoming meetings retrieved", meetings));
    }

    @GetMapping("/mentor")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Get mentor's meetings",
            description = "Get all meetings for the current mentor within a date range",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<MeetingDTO>>> getMentorMeetings(
            @Parameter(description = "Start date (ISO format)", required = true, example = "2026-03-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "End date (ISO format)", required = true, example = "2026-03-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<MeetingDTO> meetings = meetingService.getMentorMeetings(currentUser.getId(), from, to);

        return ResponseEntity.ok(ApiResponse.success("Mentor meetings retrieved", meetings));
    }

    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasAnyRole('MENTOR', 'STUDENT')")
    @Operation(
            summary = "Get batch meetings",
            description = "Get all meetings for a specific batch (mentors and enrolled students only)",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<MeetingDTO>>> getBatchMeetings(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable UUID batchId) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<MeetingDTO> meetings = meetingService.getBatchMeetings(batchId, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Batch meetings retrieved", meetings));
    }

    @DeleteMapping("/{meetingId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Cancel a meeting",
            description = "Mentor cancels a scheduled meeting",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> cancelMeeting(
            @Parameter(description = "Meeting ID", required = true)
            @PathVariable UUID meetingId) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        meetingService.cancelMeeting(meetingId, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Meeting cancelled successfully"));
    }

    @PostMapping("/webhook/zoom")
    @Operation(
            summary = "Zoom webhook endpoint",
            description = "Receives events from Zoom (meeting ended, recording ready, etc.) - Public endpoint"
    )
    public ResponseEntity<String> handleZoomWebhook(@RequestBody String payload) {
        log.info("📡 Zoom webhook received");
        meetingService.handleZoomWebhook(payload);
        return ResponseEntity.ok("Webhook received");
    }
}