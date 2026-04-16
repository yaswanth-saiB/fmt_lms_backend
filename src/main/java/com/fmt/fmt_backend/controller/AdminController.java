package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.service.RecordingService;
import com.fmt.fmt_backend.entity.Enquiry;
import com.fmt.fmt_backend.enums.BatchStatus;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.service.AdminService;
import com.fmt.fmt_backend.service.EnquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin dashboard, user management, platform overview")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final EnquiryService enquiryService;
    private final com.fmt.fmt_backend.service.MeetingService meetingService;
    private final RecordingService recordingService;

    // ---------------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------------

    @GetMapping("/dashboard")
    @Operation(summary = "Admin dashboard — platform-wide stats, upcoming classes, recent users")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard loaded", adminService.getDashboard()));
    }

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------

    @GetMapping("/users")
    @Operation(summary = "List all users — optional filter: ?role=STUDENT|MENTOR|ADMIN")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers(
            @RequestParam(required = false) UserRole role) {
        return ResponseEntity.ok(ApiResponse.success("Users fetched", adminService.getAllUsers(role)));
    }

    @PostMapping("/users")
    @Operation(summary = "Create a user (admin registers offline student — no OTP, credentials set by admin)")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse user = adminService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success("User created successfully", user));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get a user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("User fetched", adminService.getUserById(userId)));
    }

    @PutMapping("/users/{userId}/role")
    @Operation(summary = "Update a user's role (e.g. promote STUDENT → MENTOR)")
    public ResponseEntity<ApiResponse<UserResponse>> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRoleRequest request) {
        UserResponse user = adminService.updateRole(userId, request.getRole());
        return ResponseEntity.ok(ApiResponse.success("Role updated to " + request.getRole(), user));
    }

    @PutMapping("/users/{userId}/status")
    @Operation(summary = "Activate or deactivate a user account")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        UserResponse user = adminService.updateStatus(userId, request.getIsActive());
        String msg = Boolean.TRUE.equals(request.getIsActive()) ? "User activated" : "User deactivated";
        return ResponseEntity.ok(ApiResponse.success(msg, user));
    }

    @PutMapping("/users/{userId}/reset-password")
    @Operation(summary = "Admin resets a user's password (no current password needed)")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        adminService.resetPassword(userId, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Permanently delete a user and all their data",
            description = "Deletes user along with their devices, sessions, tokens, enrollments, and OTPs. " +
                    "MENTOR deletion is blocked if they have existing courses — delete those first.")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    // ---------------------------------------------------------------
    // Content Management — Courses
    // ---------------------------------------------------------------

    @PostMapping("/courses")
    @Operation(summary = "Create a course for a specific mentor")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody AdminCourseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Course created", adminService.adminCreateCourse(request)));
    }

    @PutMapping("/courses/{courseId}")
    @Operation(summary = "Update an existing course")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Course updated", adminService.adminUpdateCourse(courseId, request)));
    }

    @PutMapping("/courses/{courseId}/toggle-active")
    @Operation(summary = "Activate or deactivate a course")
    public ResponseEntity<ApiResponse<Void>> toggleCourseActive(
            @PathVariable UUID courseId,
            @RequestParam boolean isActive) {
        adminService.adminToggleCourseActive(courseId, isActive);
        String msg = isActive ? "Course activated" : "Course deactivated";
        return ResponseEntity.ok(ApiResponse.success(msg, null));
    }

    // ---------------------------------------------------------------
    // Content Management — Batches
    // ---------------------------------------------------------------

    @PostMapping("/batches")
    @Operation(summary = "Create a batch for a course (admin bypasses mentor ownership)")
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(
            @Valid @RequestBody BatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Batch created", adminService.adminCreateBatch(request)));
    }

    @PutMapping("/batches/{batchId}/status")
    @Operation(summary = "Update batch status")
    public ResponseEntity<ApiResponse<BatchResponse>> updateBatchStatus(
            @PathVariable UUID batchId,
            @Valid @RequestBody BatchStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Batch status updated",
                adminService.adminUpdateBatchStatus(batchId, request.getStatus())));
    }

    // ---------------------------------------------------------------
    // Content Management — Students / Enrollment
    // ---------------------------------------------------------------

    @GetMapping("/students/search")
    @Operation(summary = "Search students by name or email (min 2 chars)")
    public ResponseEntity<ApiResponse<List<StudentSummaryResponse>>> searchStudents(
            @RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success("Search results", adminService.adminSearchStudents(q)));
    }

    @GetMapping("/batches/{batchId}/students")
    @Operation(summary = "Get enrolled students for a batch")
    public ResponseEntity<ApiResponse<List<StudentSummaryResponse>>> getBatchStudents(
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.success("Students fetched", adminService.adminGetBatchStudents(batchId)));
    }

    @PostMapping("/batches/{batchId}/enroll")
    @Operation(summary = "Enroll a student into a batch")
    public ResponseEntity<ApiResponse<Void>> enrollStudent(
            @PathVariable UUID batchId,
            @RequestParam UUID studentId) {
        adminService.adminEnrollStudent(batchId, studentId);
        return ResponseEntity.ok(ApiResponse.success("Student enrolled", null));
    }

    @DeleteMapping("/batches/{batchId}/students/{studentId}")
    @Operation(summary = "Unenroll a student from a batch")
    public ResponseEntity<ApiResponse<Void>> unenrollStudent(
            @PathVariable UUID batchId,
            @PathVariable UUID studentId) {
        adminService.adminUnenrollStudent(batchId, studentId);
        return ResponseEntity.ok(ApiResponse.success("Student unenrolled", null));
    }

    // ---------------------------------------------------------------
    // Content Management — Meetings
    // ---------------------------------------------------------------

    @PostMapping("/meetings")
    @Operation(summary = "Create a Zoom meeting for a batch (admin)")
    public ResponseEntity<ApiResponse<MeetingResponse>> createMeeting(
            @Valid @RequestBody MeetingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Meeting created", adminService.adminCreateMeeting(request)));
    }

    @GetMapping("/batches/{batchId}/meetings")
    @Operation(summary = "Get all meetings for a batch (admin view — includes startUrl)")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getBatchMeetings(
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.success("Meetings fetched", adminService.adminGetBatchMeetings(batchId)));
    }

    @PutMapping("/meetings/{meetingId}/start")
    @Operation(summary = "Mark meeting as LIVE (admin override)")
    public ResponseEntity<ApiResponse<MeetingResponse>> startMeeting(@PathVariable UUID meetingId) {
        return ResponseEntity.ok(ApiResponse.success("Meeting is now LIVE", meetingService.adminStartMeeting(meetingId)));
    }

    @PutMapping("/meetings/{meetingId}/end")
    @Operation(summary = "Mark meeting as ENDED (admin override)")
    public ResponseEntity<ApiResponse<MeetingResponse>> endMeeting(@PathVariable UUID meetingId) {
        return ResponseEntity.ok(ApiResponse.success("Meeting marked as ENDED", meetingService.adminEndMeeting(meetingId)));
    }

    @DeleteMapping("/meetings/{meetingId}")
    @Operation(summary = "Cancel a meeting — only UPCOMING meetings can be cancelled")
    public ResponseEntity<ApiResponse<Void>> cancelMeeting(@PathVariable UUID meetingId) {
        meetingService.adminCancelMeeting(meetingId);
        return ResponseEntity.ok(ApiResponse.success("Meeting cancelled", null));
    }

    @PutMapping("/meetings/{meetingId}/reschedule")
    @Operation(summary = "Reschedule a meeting — only UPCOMING meetings")
    public ResponseEntity<ApiResponse<MeetingResponse>> rescheduleMeeting(
            @PathVariable UUID meetingId,
            @Valid @RequestBody RescheduleMeetingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Meeting rescheduled", meetingService.adminRescheduleMeeting(meetingId, request)));
    }

    // ---------------------------------------------------------------
    // OTP-Verified User Registration
    // ---------------------------------------------------------------

    @PostMapping("/users/send-otp")
    @Operation(summary = "Step 1: Send OTP to email (and mobile if provided) for verified registration")
    public ResponseEntity<ApiResponse<Void>> sendRegistrationOtp(
            @Valid @RequestBody AdminSendOtpRequest request) {
        adminService.adminSendRegistrationOtp(request);
        boolean hasMobile = request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank();
        String msg = hasMobile
                ? "OTP sent to email and mobile. Both must be verified."
                : "OTP sent to email.";
        return ResponseEntity.ok(ApiResponse.success(msg, null));
    }

    @PostMapping("/users/verify-and-create")
    @Operation(summary = "Step 2: Verify OTPs and create the user account")
    public ResponseEntity<ApiResponse<UserResponse>> verifyAndCreateUser(
            @Valid @RequestBody AdminVerifyAndCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User created successfully",
                adminService.adminVerifyAndCreateUser(request)));
    }

    // ---------------------------------------------------------------
    // Platform Overview
    // ---------------------------------------------------------------

    @GetMapping("/courses")
    @Operation(summary = "List all courses across all mentors")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getCourses() {
        return ResponseEntity.ok(ApiResponse.success("Courses fetched", adminService.getAllCourses()));
    }

    @GetMapping("/batches")
    @Operation(summary = "List all batches across all courses")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getBatches() {
        return ResponseEntity.ok(ApiResponse.success("Batches fetched", adminService.getAllBatches()));
    }

    @GetMapping("/classes")
    @Operation(summary = "List all classes (meetings) across all batches")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getClasses() {
        return ResponseEntity.ok(ApiResponse.success("Classes fetched", adminService.getAllClasses()));
    }

    @GetMapping("/recordings")
    @Operation(summary = "List all recordings across all batches")
    public ResponseEntity<ApiResponse<List<RecordingResponse>>> getRecordings() {
        return ResponseEntity.ok(ApiResponse.success("Recordings fetched", adminService.getAllRecordings()));
    }

    @PostMapping("/recordings/manual")
    @Operation(
        summary = "Manually trigger a recording when the Zoom webhook was missed (app was down)",
        description = "Admin provides the Zoom meeting ID. Backend fetches the recording URL from Zoom API " +
                      "and runs the same download → Bunny upload flow as the normal webhook. " +
                      "Use this as a fallback when the app was down during a live class. " +
                      "If the meeting is not in our system (class ran directly from Zoom), also provide batchId."
    )
    public ResponseEntity<ApiResponse<RecordingResponse>> manualRecording(
            @Valid @RequestBody ManualRecordingRequest request) {
        RecordingResponse response = recordingService.createManualRecording(request);
        recordingService.processRecordingAsync(response.getId());
        return ResponseEntity.ok(ApiResponse.success(
                "Recording queued for processing. It will be AVAILABLE in ~15–30 minutes.", response));
    }

    @PostMapping("/recordings/{recordingId}/retry")
    @Operation(
        summary = "Retry a FAILED recording",
        description = "Re-fetches a fresh Zoom download URL (the stored token may be expired) " +
                      "and re-runs the download → Bunny upload pipeline. " +
                      "Only recordings with status=FAILED can be retried."
    )
    public ResponseEntity<ApiResponse<Void>> retryRecording(@PathVariable UUID recordingId) {
        recordingService.retryFailedRecording(recordingId);
        recordingService.processRecordingAsync(recordingId);
        return ResponseEntity.ok(ApiResponse.success(
                "Recording retry started. Check status in ~15–30 minutes.", null));
    }

    // ---------------------------------------------------------------
    // Enquiries
    // ---------------------------------------------------------------

    @GetMapping("/enquiries")
    @Operation(summary = "List all enquiries — optionally filter by status")
    public ResponseEntity<ApiResponse<List<EnquiryResponse>>> getEnquiries(
            @RequestParam(required = false) Enquiry.EnquiryStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Enquiries fetched", enquiryService.getAll(status)));
    }

    @GetMapping("/enquiries/{enquiryId}")
    @Operation(summary = "Get a single enquiry by ID")
    public ResponseEntity<ApiResponse<EnquiryResponse>> getEnquiry(@PathVariable UUID enquiryId) {
        return ResponseEntity.ok(ApiResponse.success("Enquiry fetched", enquiryService.getById(enquiryId)));
    }

    @PutMapping("/enquiries/{enquiryId}/status")
    @Operation(summary = "Update enquiry status — NEW / CONTACTED / CLOSED")
    public ResponseEntity<ApiResponse<EnquiryResponse>> updateEnquiryStatus(
            @PathVariable UUID enquiryId,
            @Valid @RequestBody UpdateEnquiryStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Enquiry status updated",
                enquiryService.updateStatus(enquiryId, request.getStatus())
        ));
    }
}
