package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.WebinarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Webinars", description = "Webinar management and public registration")
public class WebinarController {

    private final WebinarService webinarService;
    private final AuthService authService;

    // ─────────────────────────────────────────────
    // Admin — CRUD  (/api/admin/webinars)
    // ─────────────────────────────────────────────

    @PostMapping("/api/admin/webinars")
    @Operation(summary = "Create a webinar", description = "Role: ADMIN")
    public ResponseEntity<ApiResponse<WebinarResponse>> create(
            @Valid @RequestBody WebinarRequest request) {
        User user = requireCurrentUser();
        ApiResponse<WebinarResponse> response = webinarService.createWebinar(request, user);
        return ResponseEntity.status(response.isSuccess() ? 201 : 400).body(response);
    }

    @PutMapping("/api/admin/webinars/{id}")
    @Operation(summary = "Update a webinar", description = "Role: ADMIN")
    public ResponseEntity<ApiResponse<WebinarResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody WebinarRequest request) {
        ApiResponse<WebinarResponse> response = webinarService.updateWebinar(id, request);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    @PutMapping("/api/admin/webinars/{id}/toggle-active")
    @Operation(summary = "Toggle webinar active/inactive", description = "Role: ADMIN")
    public ResponseEntity<ApiResponse<WebinarResponse>> toggleActive(@PathVariable UUID id) {
        ApiResponse<WebinarResponse> response = webinarService.toggleActive(id);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    @GetMapping("/api/admin/webinars")
    @Operation(summary = "List all webinars (including inactive)", description = "Role: ADMIN")
    public ResponseEntity<ApiResponse<List<WebinarResponse>>> getAllAdmin() {
        return ResponseEntity.ok(webinarService.getAllWebinars());
    }

    @GetMapping("/api/admin/webinars/{id}")
    @Operation(summary = "Get a single webinar by ID", description = "Role: ADMIN")
    public ResponseEntity<ApiResponse<WebinarResponse>> getByIdAdmin(@PathVariable UUID id) {
        ApiResponse<WebinarResponse> response = webinarService.getWebinarById(id);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    @GetMapping("/api/admin/webinars/{id}/registrations")
    @Operation(summary = "List registrations for a webinar", description = "Role: ADMIN")
    public ResponseEntity<ApiResponse<List<WebinarRegistrationResponse>>> getRegistrations(@PathVariable UUID id) {
        ApiResponse<List<WebinarRegistrationResponse>> response = webinarService.getRegistrations(id);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    @PutMapping("/api/admin/webinars/{id}/registrations/{regId}/mark-attended")
    @Operation(summary = "Mark a registrant as attended", description = "Role: ADMIN")
    public ResponseEntity<ApiResponse<WebinarRegistrationResponse>> markAttended(
            @PathVariable UUID id,
            @PathVariable UUID regId) {
        ApiResponse<WebinarRegistrationResponse> response = webinarService.markAttended(id, regId);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    // ─────────────────────────────────────────────
    // Sales view  (/api/sales/webinars)
    // ─────────────────────────────────────────────

    @GetMapping("/api/sales/webinars")
    @Operation(summary = "List active webinars (sales view)", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<List<WebinarResponse>>> getAllSales() {
        return ResponseEntity.ok(webinarService.getAllWebinars());
    }

    @GetMapping("/api/sales/webinars/{id}/registrations")
    @Operation(summary = "List registrations for a webinar (sales view)", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<List<WebinarRegistrationResponse>>> getRegistrationsSales(@PathVariable UUID id) {
        ApiResponse<List<WebinarRegistrationResponse>> response = webinarService.getRegistrations(id);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    // ─────────────────────────────────────────────
    // Public  (/api/public/webinars)
    // ─────────────────────────────────────────────

    @GetMapping("/api/public/webinars")
    @Operation(summary = "List active webinars (public)", description = "No auth required")
    public ResponseEntity<ApiResponse<List<WebinarResponse>>> getActivePublic() {
        return ResponseEntity.ok(webinarService.getActiveWebinars());
    }

    @GetMapping("/api/public/webinars/{id}")
    @Operation(summary = "Get a single webinar by ID (public)", description = "No auth required")
    public ResponseEntity<ApiResponse<WebinarResponse>> getByIdPublic(@PathVariable UUID id) {
        ApiResponse<WebinarResponse> response = webinarService.getWebinarById(id);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    @PostMapping("/api/public/webinars/{id}/register")
    @Operation(summary = "Register for a webinar (public)", description = "No auth required. Auto-creates a lead if phone is new.")
    public ResponseEntity<ApiResponse<WebinarRegistrationResponse>> register(
            @PathVariable UUID id,
            @Valid @RequestBody WebinarRegistrationRequest request) {
        ApiResponse<WebinarRegistrationResponse> response = webinarService.register(id, request);
        return ResponseEntity.status(response.isSuccess() ? 201 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────

    private User requireCurrentUser() {
        Optional<User> user = authService.getCurrentUser();
        if (user.isEmpty()) throw new RuntimeException("Not authenticated");
        return user.get();
    }
}
