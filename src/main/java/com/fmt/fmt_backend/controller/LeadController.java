package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.Enquiry;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.UserRepository;
import com.fmt.fmt_backend.service.AdminService;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.EnquiryService;
import com.fmt.fmt_backend.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Management CRM", description = "Sales pipeline and enquiries for ADMIN and SALES roles")
public class LeadController {

    private final LeadService leadService;
    private final EnquiryService enquiryService;
    private final AuthService authService;
    private final AdminService adminService;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────
    // Import from Excel
    // ─────────────────────────────────────────────

    @PostMapping(value = "/leads/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import leads from Excel (.xlsx)", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadImportResponse>> importLeads(
            @RequestParam("file") MultipartFile file) {

        User user = requireCurrentUser();
        log.info("📥 Import leads request from: {}", user.getEmail());
        ApiResponse<LeadImportResponse> response = leadService.importFromExcel(file, user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Stats (must be before /{id} to avoid routing conflict)
    // ─────────────────────────────────────────────

    @GetMapping("/leads/stats")
    @Operation(summary = "Dashboard stats — totals per status + demo counts by period", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadStatsResponse>> getStats() {
        ApiResponse<LeadStatsResponse> response = leadService.getStats();
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    // Add single lead
    // ─────────────────────────────────────────────

    @PostMapping("/leads")
    @Operation(summary = "Add a single lead manually", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadResponse>> addLead(
            @Valid @RequestBody LeadRequest request) {

        User user = requireCurrentUser();
        ApiResponse<LeadResponse> response = leadService.addLead(request, user);
        return ResponseEntity.status(response.isSuccess() ? 201 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // List leads (paginated, filterable)
    // ─────────────────────────────────────────────

    @GetMapping("/leads")
    @Operation(summary = "List all leads with optional filters and pagination", description = "Role: ADMIN or SALES. Params: status, stage (ACTIVE|INACTIVE), assignedTo (UUID), search, page, size")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLeads(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ApiResponse<Map<String, Object>> response = leadService.getLeads(status, stage, assignedTo, search, page, size);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    // Single lead detail
    // ─────────────────────────────────────────────

    @GetMapping("/leads/{id}")
    @Operation(summary = "Get full lead detail with activity history", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadResponse>> getLeadById(@PathVariable UUID id) {
        ApiResponse<LeadResponse> response = leadService.getLeadById(id);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    // ─────────────────────────────────────────────
    // Log failed call (DNP)
    // ─────────────────────────────────────────────

    @PutMapping("/leads/{id}/call-attempted")
    @Operation(summary = "Log a failed call attempt (DNP). Auto-increments DNP count and sets whatsappEligible after 5 DNPs", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadResponse>> callAttempted(@PathVariable UUID id) {
        User user = requireCurrentUser();
        ApiResponse<LeadResponse> response = leadService.callAttempted(id, user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Mark WhatsApp sent
    // ─────────────────────────────────────────────

    @PutMapping("/leads/{id}/whatsapp-sent")
    @Operation(summary = "Mark WhatsApp as sent (only allowed after 5 DNPs)", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadResponse>> markWhatsappSent(@PathVariable UUID id) {
        User user = requireCurrentUser();
        ApiResponse<LeadResponse> response = leadService.markWhatsappSent(id, user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Update status
    // ─────────────────────────────────────────────

    @PutMapping("/leads/{id}/status")
    @Operation(summary = "Update lead status manually", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeadStatusRequest request) {

        User user = requireCurrentUser();
        ApiResponse<LeadResponse> response = leadService.updateStatus(id, request, user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Add note
    // ─────────────────────────────────────────────

    @PostMapping("/leads/{id}/note")
    @Operation(summary = "Add a note to the lead", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadResponse>> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody LeadNoteRequest request) {

        User user = requireCurrentUser();
        ApiResponse<LeadResponse> response = leadService.addNote(id, request, user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Assign lead — ADMIN only (enforced in SecurityConfig)
    // ─────────────────────────────────────────────

    @PutMapping("/leads/{id}/assign")
    @Operation(summary = "Assign lead to a SALES user (ADMIN only)", description = "Role: ADMIN only")
    public ResponseEntity<ApiResponse<LeadResponse>> assignLead(
            @PathVariable UUID id,
            @Valid @RequestBody LeadAssignRequest request) {

        User user = requireCurrentUser();
        ApiResponse<LeadResponse> response = leadService.assignLead(id, request, user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Google Sheets Sync
    // ─────────────────────────────────────────────

    @PostMapping("/leads/sync-sheets")
    @Operation(summary = "Manually trigger Google Sheets sync", description = "Role: ADMIN or SALES. Pulls latest rows from FMT Ad Leads sheet and imports new leads.")
    public ResponseEntity<ApiResponse<LeadImportResponse>> syncFromSheets() {
        User user = requireCurrentUser();
        log.info("📊 Manual sheet sync triggered by: {}", user.getEmail());
        ApiResponse<LeadImportResponse> response = leadService.syncFromSheets(user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @GetMapping("/leads/sync-logs")
    @Operation(summary = "Get last 20 Google Sheets sync logs", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<List<SheetSyncLogResponse>>> getSyncLogs() {
        ApiResponse<List<SheetSyncLogResponse>> response = leadService.getSyncLogs();
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    // Enquiries (ADMIN + SALES — same data as /api/admin/enquiries)
    // ─────────────────────────────────────────────

    @GetMapping("/enquiries")
    @Operation(summary = "List all enquiries, optionally filtered by status (NEW | CONTACTED | CLOSED)", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<List<EnquiryResponse>>> getEnquiries(
            @RequestParam(required = false) String status) {

        Enquiry.EnquiryStatus statusEnum = parseEnquiryStatus(status);
        List<EnquiryResponse> enquiries = enquiryService.getAll(statusEnum);
        return ResponseEntity.ok(ApiResponse.success("Enquiries retrieved", enquiries));
    }

    @GetMapping("/enquiries/{id}")
    @Operation(summary = "Get single enquiry by ID", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<EnquiryResponse>> getEnquiryById(@PathVariable UUID id) {
        try {
            EnquiryResponse enquiry = enquiryService.getById(id);
            return ResponseEntity.ok(ApiResponse.success("Enquiry found", enquiry));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/enquiries/{id}/status")
    @Operation(summary = "Update enquiry status (NEW → CONTACTED → CLOSED)", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<EnquiryResponse>> updateEnquiryStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEnquiryStatusRequest request) {
        try {
            EnquiryResponse updated = enquiryService.updateStatus(id, request.getStatus());
            return ResponseEntity.ok(ApiResponse.success("Enquiry status updated", updated));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────
    // WhatsApp campaign step
    // ─────────────────────────────────────────────

    @PostMapping("/leads/{id}/whatsapp-step")
    @Operation(summary = "Log a WhatsApp campaign sequence step", description = "Role: ADMIN or SALES. Body: { step: 1, message: '...' }")
    public ResponseEntity<ApiResponse<LeadResponse>> logWhatsappStep(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        User user = requireCurrentUser();
        int step = body.containsKey("step") ? Integer.parseInt(body.get("step").toString()) : 1;
        String message = body.containsKey("message") ? body.get("message").toString() : null;
        ApiResponse<LeadResponse> response = leadService.logWhatsappStep(id, step, message, user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Payment management
    // ─────────────────────────────────────────────

    @PostMapping("/leads/{id}/payments")
    @Operation(summary = "Record a payment for a lead", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadPaymentResponse>> addPayment(
            @PathVariable UUID id,
            @Valid @RequestBody LeadPaymentRequest request) {

        User user = requireCurrentUser();
        ApiResponse<LeadPaymentResponse> response = leadService.addPayment(id, request, user);
        return ResponseEntity.status(response.isSuccess() ? 201 : 400).body(response);
    }

    @GetMapping("/leads/{id}/payments")
    @Operation(summary = "Get all payments for a lead with balance summary", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPayments(@PathVariable UUID id) {
        ApiResponse<Map<String, Object>> response = leadService.getPayments(id);
        return ResponseEntity.status(response.isSuccess() ? 200 : 404).body(response);
    }

    @PutMapping("/leads/{id}/payments/{paymentId}/mark-paid")
    @Operation(summary = "Mark a pending payment as paid", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<LeadPaymentResponse>> markPaymentPaid(
            @PathVariable UUID id,
            @PathVariable UUID paymentId) {

        User user = requireCurrentUser();
        ApiResponse<LeadPaymentResponse> response = leadService.markPaymentPaid(id, paymentId, user);
        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    // ─────────────────────────────────────────────
    // Per-rep stats
    // ─────────────────────────────────────────────

    @GetMapping("/stats/reps")
    @Operation(summary = "Per-sales-rep performance breakdown", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<List<SalesRepStatsResponse>>> getRepStats() {
        ApiResponse<List<SalesRepStatsResponse>> response = leadService.getRepStats();
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    // Mentor list (for demo booking dropdown)
    // ─────────────────────────────────────────────

    @GetMapping("/mentors")
    @Operation(summary = "List all active mentors (for demo booking dropdown)", description = "Role: ADMIN or SALES")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getMentors() {
        List<UserResponse> mentors = userRepository.findAllByUserRoleOrderByCreatedAtDesc(UserRole.MENTOR)
                .stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .map(adminService::toUserResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Mentors retrieved", mentors));
    }

    // ─────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────

    private User requireCurrentUser() {
        Optional<User> user = authService.getCurrentUser();
        if (user.isEmpty()) throw new RuntimeException("Not authenticated");
        return user.get();
    }

    private Enquiry.EnquiryStatus parseEnquiryStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return Enquiry.EnquiryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
