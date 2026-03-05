package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.BatchDTO;
import com.fmt.fmt_backend.dto.CreateBatchRequest;
import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.StudentDTO;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.BatchService;
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
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Batches", description = "Batch management APIs for mentors and students")
public class BatchController {

    private final BatchService batchService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Create a new batch",
            description = "Mentor creates a batch for their course with start/end dates and student limit",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch created successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or invalid dates"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not authorized to create batch for this course")
    })
    public ResponseEntity<ApiResponse<BatchDTO>> createBatch(
            @Valid @RequestBody CreateBatchRequest request) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        BatchDTO batch = batchService.createBatch(currentUser.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Batch created successfully", batch));
    }

    @GetMapping("/mentor")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Get mentor's batches",
            description = "Get all batches created by the current mentor",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<BatchDTO>>> getMentorBatches() {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<BatchDTO> batches = batchService.getMentorBatches(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Your batches retrieved", batches));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Get student's batches",
            description = "Get all batches the current student is enrolled in",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<BatchDTO>>> getStudentBatches() {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<BatchDTO> batches = batchService.getStudentBatches(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Your enrolled batches retrieved", batches));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get batch details by ID",
            description = "Get detailed information about a specific batch"
    )
    public ResponseEntity<ApiResponse<BatchDTO>> getBatchById(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable UUID id) {

        BatchDTO batch = batchService.getBatchById(id);
        return ResponseEntity.ok(ApiResponse.success("Batch details retrieved", batch));
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Get students in a batch",
            description = "Mentor can view all students enrolled in their batch",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<StudentDTO>>> getBatchStudents(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable UUID id) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<StudentDTO> students = batchService.getBatchStudents(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Students retrieved", students));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Update batch status",
            description = "Mentor can update batch status (UPCOMING, ONGOING, COMPLETED, CANCELLED)",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<BatchDTO>> updateBatchStatus(
            @Parameter(description = "Batch ID", required = true)
            @PathVariable UUID id,
            @RequestParam String status) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        BatchDTO batch = batchService.updateBatchStatus(id, currentUser.getId(), status);
        return ResponseEntity.ok(ApiResponse.success("Batch status updated successfully", batch));
    }
}