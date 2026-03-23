package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.Enquiry;
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
