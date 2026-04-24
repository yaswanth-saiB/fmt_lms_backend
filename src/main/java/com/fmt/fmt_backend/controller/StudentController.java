package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.BatchService;
import com.fmt.fmt_backend.service.MeetingService;
import com.fmt.fmt_backend.service.RecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@Tag(name = "Student", description = "Student dashboard, enrolled courses, schedule, and recordings")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final AuthService authService;
    private final BatchService batchService;
    private final MeetingService meetingService;
    private final RecordingService recordingService;

    // ---------------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------------

    @GetMapping("/dashboard")
    @Operation(summary = "Student dashboard — enrolled courses + upcoming classes")
    public ResponseEntity<ApiResponse<StudentDashboardResponse>> dashboard() {
        User student = currentStudent();
        UUID studentId = student.getId();

        List<BatchResponse> enrolledBatches = batchService.getStudentEnrolledBatches(studentId);
        List<MeetingResponse> upcomingClasses = meetingService.getUpcomingMeetingsForStudent(studentId)
                .stream().limit(5).toList();

        StudentDashboardResponse dashboard = StudentDashboardResponse.builder()
                .enrolledCoursesCount(enrolledBatches.size())
                .enrolledBatches(enrolledBatches)
                .upcomingClasses(upcomingClasses)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Dashboard loaded", dashboard));
    }

    // ---------------------------------------------------------------
    // My Courses
    // ---------------------------------------------------------------

    @GetMapping("/courses")
    @Operation(summary = "My enrolled courses (batches)")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getMyCourses() {
        List<BatchResponse> batches = batchService.getStudentEnrolledBatches(currentStudent().getId());
        return ResponseEntity.ok(ApiResponse.success("Courses fetched", batches));
    }

    // ---------------------------------------------------------------
    // Schedule (Upcoming Classes)
    // ---------------------------------------------------------------

    @GetMapping("/schedule")
    @Operation(summary = "Upcoming classes for all enrolled batches")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getSchedule() {
        List<MeetingResponse> upcoming = meetingService.getUpcomingMeetingsForStudent(currentStudent().getId());
        return ResponseEntity.ok(ApiResponse.success("Schedule fetched", upcoming));
    }

    // ---------------------------------------------------------------
    // Batch-specific meetings (join URL)
    // ---------------------------------------------------------------

    @GetMapping("/batches/{batchId}/classes")
    @Operation(summary = "Get all classes for a batch I'm enrolled in",
            description = "Optional filter: ?status=UPCOMING,LIVE,ENDED,CANCELLED (comma-separated). Omit for all.")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getBatchClasses(
            @PathVariable UUID batchId,
            @RequestParam(required = false) List<com.fmt.fmt_backend.enums.MeetingStatus> status) {
        List<MeetingResponse> meetings = meetingService.getBatchMeetingsForStudent(batchId, currentStudent().getId(), status);
        return ResponseEntity.ok(ApiResponse.success("Classes fetched", meetings));
    }

    @GetMapping("/classes/{meetingId}/join")
    @Operation(summary = "Get join URL for a specific class (must be enrolled in the batch)")
    public ResponseEntity<ApiResponse<MeetingResponse>> getJoinUrl(@PathVariable UUID meetingId) {
        MeetingResponse meeting = meetingService.getJoinUrlForStudent(meetingId, currentStudent().getId());
        return ResponseEntity.ok(ApiResponse.success("Join URL ready", meeting));
    }

    // ---------------------------------------------------------------
    // Recordings
    // ---------------------------------------------------------------

    @GetMapping("/batches/{batchId}/recordings")
    @Operation(summary = "List available recordings for a batch I'm enrolled in",
               description = "Only returns AVAILABLE recordings. Student must be enrolled in the batch. Latest first.")
    public ResponseEntity<ApiResponse<List<StudentRecordingResponse>>> getBatchRecordings(
            @PathVariable UUID batchId) {
        List<StudentRecordingResponse> recordings =
                recordingService.getAvailableRecordingsForBatch(batchId, currentStudent());
        return ResponseEntity.ok(ApiResponse.success("Recordings fetched", recordings));
    }

    @GetMapping("/recordings/{recordingId}/play")
    @Operation(summary = "Get signed play URL for a recording",
               description = "Returns a Bunny.net signed URL valid for 3 hours. " +
                             "Student must be enrolled in the recording's batch. " +
                             "Call this fresh each time — do not cache the URL.")
    public ResponseEntity<ApiResponse<PlayUrlResponse>> getPlayUrl(@PathVariable UUID recordingId) {
        PlayUrlResponse response = recordingService.generatePlayUrl(recordingId, currentStudent());
        return ResponseEntity.ok(ApiResponse.success("Play URL generated", response));
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private User currentStudent() {
        return authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));
    }
}
