package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.EnrollmentDTO;
import com.fmt.fmt_backend.dto.EnrollmentRequest;
import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.EnrollmentService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Enrollments", description = "Student enrollment management APIs")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Enroll in a batch",
            description = "Student enrolls themselves in a batch",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Enrollment successful",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Batch full or already enrolled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a student"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<ApiResponse<EnrollmentDTO>> enroll(
            @Valid @RequestBody EnrollmentRequest request) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        EnrollmentDTO enrollment = enrollmentService.enroll(currentUser.getId(), request.getBatchId());

        return ResponseEntity.ok(ApiResponse.success("Successfully enrolled in batch", enrollment));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Get student's enrollments",
            description = "Get all enrollments for the current student",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<EnrollmentDTO>>> getStudentEnrollments() {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<EnrollmentDTO> enrollments = enrollmentService.getStudentEnrollments(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Your enrollments retrieved", enrollments));
    }

    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Get batch enrollments",
            description = "Mentor can view all enrollments for their batch",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<EnrollmentDTO>>> getBatchEnrollments(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable UUID batchId) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<EnrollmentDTO> enrollments = enrollmentService.getBatchEnrollments(batchId, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Batch enrollments retrieved", enrollments));
    }

    @PutMapping("/{enrollmentId}/progress")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Update student progress",
            description = "Mentor updates progress percentage for a student",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<EnrollmentDTO>> updateProgress(
            @Parameter(description = "Enrollment ID", required = true)
            @PathVariable UUID enrollmentId,
            @RequestParam Integer progress) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        EnrollmentDTO enrollment = enrollmentService.updateProgress(enrollmentId, currentUser.getId(), progress);

        return ResponseEntity.ok(ApiResponse.success("Progress updated successfully", enrollment));
    }

    @PostMapping("/{enrollmentId}/drop")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Drop from batch",
            description = "Student drops from an enrolled batch",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<EnrollmentDTO>> dropFromBatch(
            @Parameter(description = "Enrollment ID", required = true)
            @PathVariable UUID enrollmentId) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        EnrollmentDTO enrollment = enrollmentService.dropFromBatch(enrollmentId, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Dropped from batch successfully", enrollment));
    }
}