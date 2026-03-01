package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.BatchResponse;
import com.fmt.fmt_backend.dto.CreateBatchRequest;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Batches", description = "Batch management APIs")
public class BatchController {

    private final BatchService batchService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Create a new batch (Mentor only)")
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(
            @Valid @RequestBody CreateBatchRequest request) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        BatchResponse batch = batchService.createBatch(currentUser.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Batch created successfully", batch));
    }

    @GetMapping("/mentor")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Get all batches for current mentor")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getMentorBatches() {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<BatchResponse> batches = batchService.getMentorBatches(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Mentor batches retrieved", batches));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get all batches for current student")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getStudentBatches() {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<BatchResponse> batches = batchService.getStudentBatches(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success("Your batches retrieved", batches));
    }
}
