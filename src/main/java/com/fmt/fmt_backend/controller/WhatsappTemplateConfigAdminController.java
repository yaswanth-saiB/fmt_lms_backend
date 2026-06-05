package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.WhatsappTemplateConfigDto;
import com.fmt.fmt_backend.dto.WhatsappTemplateConfigRequest;
import com.fmt.fmt_backend.repository.UserRepository;
import com.fmt.fmt_backend.service.WhatsappTemplateConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for per-template config (header media id + button → action map).
 * ADMIN role only — gated by /api/admin/** matcher in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/whatsapp-templates")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Template Config", description = "Per-template media id and button action map — ADMIN only")
public class WhatsappTemplateConfigAdminController {

    private final WhatsappTemplateConfigService service;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List all template configs")
    public ResponseEntity<ApiResponse<List<WhatsappTemplateConfigDto>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Template configs fetched", service.getAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a template config by id")
    public ResponseEntity<ApiResponse<WhatsappTemplateConfigDto>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Template config fetched", service.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new template config")
    public ResponseEntity<ApiResponse<WhatsappTemplateConfigDto>> create(
            @Valid @RequestBody WhatsappTemplateConfigRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = extractUserId(principal);
        return ResponseEntity.status(201)
                .body(ApiResponse.success("Template config created", service.create(req, userId)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a template config")
    public ResponseEntity<ApiResponse<WhatsappTemplateConfigDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody WhatsappTemplateConfigRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Template config updated", service.update(id, req)));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Toggle is_active on a template config")
    public ResponseEntity<ApiResponse<WhatsappTemplateConfigDto>> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Toggled", service.toggleActive(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a template config")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Template config deleted"));
    }

    private UUID extractUserId(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
