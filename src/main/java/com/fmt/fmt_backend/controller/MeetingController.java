package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.CreateMeetingRequest;
import com.fmt.fmt_backend.dto.MeetingResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Meetings", description = "Meeting management APIs")
public class MeetingController {

    private final MeetingService meetingService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Create a new meeting (Mentor only)")
    public ResponseEntity<ApiResponse<MeetingResponse>> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        MeetingResponse meeting = meetingService.createMeeting(currentUser.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Meeting created successfully", meeting));
    }

    @GetMapping("/student/upcoming")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get upcoming meetings for student")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getStudentUpcomingMeetings() {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<MeetingResponse> meetings = meetingService.getStudentUpcomingMeetings(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Upcoming meetings retrieved", meetings));
    }

    @GetMapping("/mentor")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Get mentor meetings in date range")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getMentorMeetings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<MeetingResponse> meetings = meetingService.getMentorMeetings(currentUser.getId(), from, to);

        return ResponseEntity.ok(ApiResponse.success("Mentor meetings retrieved", meetings));
    }

    @DeleteMapping("/{meetingId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Cancel a meeting")
    public ResponseEntity<ApiResponse<String>> cancelMeeting(@PathVariable UUID meetingId) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        meetingService.cancelMeeting(meetingId, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Meeting cancelled successfully"));
    }
}