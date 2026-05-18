package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.entity.LeadActivity;
import com.fmt.fmt_backend.entity.LeadPayment;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final LeadPaymentRepository leadPaymentRepository;
    private final UserRepository userRepository;
    private final ExcelImportService excelImportService;
    private final SheetSyncService sheetSyncService;
    private final SheetSyncLogRepository sheetSyncLogRepository;

    // Terminal statuses — excluded from aging/stale/active counts
    private static final List<LeadStatus> TERMINAL_STATUSES = List.of(
            LeadStatus.PAYMENT_DONE, LeadStatus.NOT_INTERESTED, LeadStatus.SWITCH_OFF);

    private static final List<LeadStatus> ACTIVE_STATUSES = List.of(
            LeadStatus.NEW, LeadStatus.DNP_1, LeadStatus.DNP_2, LeadStatus.DNP_3,
            LeadStatus.DNP_4, LeadStatus.DNP_5, LeadStatus.WHATSAPP_SENT,
            LeadStatus.WHATSAPP_RESPONDED, LeadStatus.CONTACTED,
            LeadStatus.FOLLOWUP_SCHEDULED, LeadStatus.DEMO_BOOKED,
            LeadStatus.DEMO_DONE, LeadStatus.DEMO_NO_SHOW, LeadStatus.CLOSING);

    // ─────────────────────────────────────────────
    // Import
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadImportResponse> importFromExcel(MultipartFile file, User currentUser) {
        log.info("📥 Excel import requested by: {}", currentUser.getEmail());
        try {
            LeadImportResponse result = excelImportService.importLeads(file, currentUser);
            return ApiResponse.success("Import complete", result);
        } catch (IOException e) {
            log.error("❌ Failed to read Excel file: {}", e.getMessage());
            return ApiResponse.error("Failed to read Excel file: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    // Add single lead manually
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadResponse> addLead(LeadRequest request, User currentUser) {
        log.info("➕ Adding lead manually — phone: {}", request.getPhone());

        if (leadRepository.existsByPhone(request.getPhone())) {
            return ApiResponse.error("A lead with this phone number already exists");
        }

        Lead lead = Lead.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .courseInterest(request.getCourseInterest())
                .source(LeadSource.META_ADS)
                .status(LeadStatus.NEW)
                .notes(request.getNotes())
                .build();

        lead = leadRepository.save(lead);
        leadActivityRepository.save(activity(lead, ActivityType.LEAD_IMPORTED, "Lead added manually", currentUser));
        return ApiResponse.success("Lead created", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Paginated list with filters
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getLeads(
            String status, String stage, UUID assignedTo, String search, int page, int size) {

        Specification<Lead> spec = (root, query, cb) -> cb.conjunction();

        // Status filter takes priority over stage filter
        LeadStatus statusEnum = parseStatus(status);
        if (statusEnum != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusEnum));
        } else if (stage != null && !stage.isBlank()) {
            List<LeadStatus> stageStatuses = "INACTIVE".equalsIgnoreCase(stage)
                    ? TERMINAL_STATUSES
                    : ACTIVE_STATUSES;
            spec = spec.and((root, query, cb) -> root.get("status").in(stageStatuses));
        }

        if (assignedTo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), assignedTo));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(root.get("phone"), "%" + search + "%")
            ));
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Lead> leadsPage = leadRepository.findAll(spec, pageable);

        List<LeadSummaryResponse> content = leadsPage.getContent()
                .stream()
                .map(LeadSummaryResponse::from)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", leadsPage.getTotalElements());
        result.put("totalPages", leadsPage.getTotalPages());
        result.put("currentPage", page);
        return ApiResponse.success("Leads retrieved", result);
    }

    // ─────────────────────────────────────────────
    // Single lead detail
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<LeadResponse> getLeadById(UUID id) {
        return leadRepository.findById(id)
                .map(lead -> ApiResponse.success("Lead found", buildFullResponse(lead)))
                .orElse(ApiResponse.error("Lead not found"));
    }

    // ─────────────────────────────────────────────
    // Log failed call attempt (DNP)
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadResponse> callAttempted(UUID id, User currentUser) {
        Optional<Lead> opt = leadRepository.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("Lead not found");

        Lead lead = opt.get();
        if (lead.getDnpCount() >= 5) {
            return ApiResponse.error("Maximum call attempts reached. Send WhatsApp.");
        }

        int newCount = lead.getDnpCount() + 1;
        lead.setDnpCount(newCount);
        lead.setStatus(LeadStatus.valueOf("DNP_" + newCount));
        lead.setLastCallAt(LocalDateTime.now());

        if (newCount == 5) {
            lead.setWhatsappEligible(true);
            log.info("📱 Lead {} now eligible for WhatsApp after 5 DNPs", id);
        }

        autoAssignIfUnassigned(lead, currentUser);
        lead = leadRepository.save(lead);
        leadActivityRepository.save(activity(lead, ActivityType.CALL_ATTEMPTED,
                "DNP " + newCount + " — called, no answer", currentUser));
        return ApiResponse.success("Call attempt logged", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Mark WhatsApp sent
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadResponse> markWhatsappSent(UUID id, User currentUser) {
        Optional<Lead> opt = leadRepository.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("Lead not found");

        Lead lead = opt.get();
        if (!Boolean.TRUE.equals(lead.getWhatsappEligible())) {
            return ApiResponse.error("Lead is not eligible for WhatsApp yet. Complete 5 call attempts first.");
        }

        lead.setWhatsappSent(true);
        lead.setStatus(LeadStatus.WHATSAPP_SENT);
        autoAssignIfUnassigned(lead, currentUser);
        lead = leadRepository.save(lead);
        leadActivityRepository.save(activity(lead, ActivityType.WHATSAPP_SENT,
                "WhatsApp sent after 5 failed call attempts", currentUser));
        return ApiResponse.success("WhatsApp marked as sent", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Log WhatsApp campaign sequence step
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadResponse> logWhatsappStep(UUID id, int step, String message, User currentUser) {
        Optional<Lead> opt = leadRepository.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("Lead not found");

        Lead lead = opt.get();
        String description = "WhatsApp step " + step + (message != null ? ": " + message : "");
        leadActivityRepository.save(activity(lead, ActivityType.WHATSAPP_SEQUENCE, description, currentUser));
        return ApiResponse.success("WhatsApp step logged", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Update status
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadResponse> updateStatus(UUID id, UpdateLeadStatusRequest request, User currentUser) {
        Optional<Lead> opt = leadRepository.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("Lead not found");

        Lead lead = opt.get();
        LeadStatus oldStatus = lead.getStatus();

        lead.setStatus(request.getStatus());

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            lead.setNotes(request.getNotes());
        }
        if (request.getFollowupDatetime() != null) {
            lead.setFollowupDatetime(request.getFollowupDatetime());
        }

        switch (request.getStatus()) {
            case DEMO_BOOKED -> {
                if (request.getDemoMentorId() != null) {
                    userRepository.findById(request.getDemoMentorId()).ifPresent(lead::setDemoMentor);
                }
                if (request.getDemoScheduledAt() != null) lead.setDemoScheduledAt(request.getDemoScheduledAt());
                if (request.getDemoType() != null) lead.setDemoType(request.getDemoType());
            }
            case DEMO_DONE -> lead.setDemoConductedAt(
                    request.getDemoConductedAt() != null ? request.getDemoConductedAt() : LocalDateTime.now());
            case CLOSING -> {
                if (request.getClosingBlocker() != null) lead.setClosingBlocker(request.getClosingBlocker());
                if (request.getClosingComment() != null) lead.setClosingComment(request.getClosingComment());
                if (request.getCourseFee() != null) lead.setCourseFee(request.getCourseFee());
            }
            case PAYMENT_DONE -> {
                lead.setClosedBy(currentUser);
                if (request.getCourseFee() != null) lead.setCourseFee(request.getCourseFee());
            }
            case WHATSAPP_RESPONDED -> {
                if (request.getAlternatePhone() != null && !request.getAlternatePhone().isBlank()) {
                    lead.setAlternatePhone(request.getAlternatePhone());
                }
            }
            default -> { /* no extra fields */ }
        }

        autoAssignIfUnassigned(lead, currentUser);
        lead = leadRepository.save(lead);

        String description = "Status changed from " + oldStatus + " to " + request.getStatus();
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            description += ". Notes: " + request.getNotes();
        }

        ActivityType actType = request.getStatus() == LeadStatus.FOLLOWUP_SCHEDULED
                ? ActivityType.FOLLOWUP_SCHEDULED
                : ActivityType.STATUS_CHANGE;

        leadActivityRepository.save(activity(lead, actType, description, currentUser));
        return ApiResponse.success("Status updated", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Add note
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadResponse> addNote(UUID id, LeadNoteRequest request, User currentUser) {
        Optional<Lead> opt = leadRepository.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("Lead not found");

        Lead lead = opt.get();
        lead.setNotes(request.getNote());
        autoAssignIfUnassigned(lead, currentUser);
        lead = leadRepository.save(lead);
        leadActivityRepository.save(activity(lead, ActivityType.NOTE_ADDED, "Note: " + request.getNote(), currentUser));
        return ApiResponse.success("Note added", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Assign lead
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadResponse> assignLead(UUID id, LeadAssignRequest request, User currentUser) {
        Optional<Lead> opt = leadRepository.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("Lead not found");

        Optional<User> salesUser = userRepository.findById(request.getAssignedTo());
        if (salesUser.isEmpty()) return ApiResponse.error("Assigned user not found");

        User assignee = salesUser.get();
        if (assignee.getUserRole() != UserRole.SALES && assignee.getUserRole() != UserRole.ADMIN) {
            return ApiResponse.error("Can only assign leads to SALES or ADMIN users");
        }

        Lead lead = opt.get();
        lead.setAssignedTo(assignee);
        lead = leadRepository.save(lead);
        leadActivityRepository.save(activity(lead, ActivityType.NOTE_ADDED,
                "Lead assigned to " + assignee.getFirstName() + " " + assignee.getLastName(), currentUser));
        return ApiResponse.success("Lead assigned", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Payment management
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<LeadPaymentResponse> addPayment(UUID leadId, LeadPaymentRequest request, User currentUser) {
        Optional<Lead> opt = leadRepository.findById(leadId);
        if (opt.isEmpty()) return ApiResponse.error("Lead not found");

        Lead lead = opt.get();

        LeadPayment payment = LeadPayment.builder()
                .lead(lead)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .dueDate(request.getDueDate())
                .notes(request.getNotes())
                .status(request.isMarkPaidNow() ? PaymentStatus.PAID : PaymentStatus.PENDING)
                .paidAt(request.isMarkPaidNow() ? LocalDateTime.now() : null)
                .recordedBy(currentUser)
                .build();

        payment = leadPaymentRepository.save(payment);

        leadActivityRepository.save(activity(lead, ActivityType.PAYMENT_RECORDED,
                "Payment recorded: ₹" + request.getAmount() + " (" + request.getPaymentType() + ")" +
                (request.isMarkPaidNow() ? " — marked PAID" : " — due " + request.getDueDate()),
                currentUser));

        log.info("💰 Payment recorded for lead {} — ₹{} {}", leadId, request.getAmount(), request.getPaymentType());
        return ApiResponse.success("Payment recorded", LeadPaymentResponse.from(payment));
    }

    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getPayments(UUID leadId) {
        Optional<Lead> opt = leadRepository.findById(leadId);
        if (opt.isEmpty()) return ApiResponse.error("Lead not found");

        Lead lead = opt.get();
        List<LeadPaymentResponse> payments = leadPaymentRepository.findByLeadIdOrderByCreatedAtAsc(leadId)
                .stream().map(LeadPaymentResponse::from).collect(Collectors.toList());

        BigDecimal totalPaid = leadPaymentRepository.sumAmountByLeadIdAndStatus(leadId, PaymentStatus.PAID);
        BigDecimal balance = lead.getCourseFee() != null ? lead.getCourseFee().subtract(totalPaid) : null;

        Map<String, Object> result = new HashMap<>();
        result.put("payments", payments);
        result.put("totalPaid", totalPaid);
        result.put("courseFee", lead.getCourseFee());
        result.put("balance", balance);
        return ApiResponse.success("Payments retrieved", result);
    }

    @Transactional
    public ApiResponse<LeadPaymentResponse> markPaymentPaid(UUID leadId, UUID paymentId, User currentUser) {
        if (!leadRepository.existsById(leadId)) return ApiResponse.error("Lead not found");

        Optional<LeadPayment> opt = leadPaymentRepository.findById(paymentId);
        if (opt.isEmpty()) return ApiResponse.error("Payment not found");

        LeadPayment payment = opt.get();
        if (!payment.getLead().getId().equals(leadId)) return ApiResponse.error("Payment does not belong to this lead");
        if (payment.getStatus() == PaymentStatus.PAID) return ApiResponse.error("Payment is already marked as paid");

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment = leadPaymentRepository.save(payment);

        leadActivityRepository.save(activity(payment.getLead(), ActivityType.PAYMENT_RECORDED,
                "Payment of ₹" + payment.getAmount() + " marked as PAID", currentUser));

        return ApiResponse.success("Payment marked as paid", LeadPaymentResponse.from(payment));
    }

    @Transactional
    public ApiResponse<LeadPaymentResponse> editPayment(UUID leadId, UUID paymentId,
                                                         UpdateLeadPaymentRequest request, User currentUser) {
        if (!leadRepository.existsById(leadId)) return ApiResponse.error("Lead not found");

        Optional<LeadPayment> opt = leadPaymentRepository.findById(paymentId);
        if (opt.isEmpty()) return ApiResponse.error("Payment not found");

        LeadPayment payment = opt.get();
        if (!payment.getLead().getId().equals(leadId)) return ApiResponse.error("Payment does not belong to this lead");

        BigDecimal oldAmount = payment.getAmount();
        PaymentType oldType = payment.getPaymentType();

        payment.setAmount(request.getAmount());
        payment.setPaymentType(request.getPaymentType());
        payment.setDueDate(request.getDueDate());
        if (request.getNotes() != null) payment.setNotes(request.getNotes());
        payment = leadPaymentRepository.save(payment);

        leadActivityRepository.save(activity(payment.getLead(), ActivityType.PAYMENT_RECORDED,
                "Payment updated: ₹" + oldAmount + " (" + oldType + ") → ₹" + request.getAmount() + " (" + request.getPaymentType() + ")",
                currentUser));

        log.info("✏️ Payment {} updated by {} — ₹{} → ₹{}", paymentId, currentUser.getEmail(), oldAmount, request.getAmount());
        return ApiResponse.success("Payment updated", LeadPaymentResponse.from(payment));
    }

    @Transactional
    public ApiResponse<String> deletePayment(UUID leadId, UUID paymentId, User currentUser) {
        if (!leadRepository.existsById(leadId)) return ApiResponse.error("Lead not found");

        Optional<LeadPayment> opt = leadPaymentRepository.findById(paymentId);
        if (opt.isEmpty()) return ApiResponse.error("Payment not found");

        LeadPayment payment = opt.get();
        if (!payment.getLead().getId().equals(leadId)) return ApiResponse.error("Payment does not belong to this lead");

        Lead lead = payment.getLead();
        BigDecimal amount = payment.getAmount();
        PaymentType type = payment.getPaymentType();
        PaymentStatus status = payment.getStatus();

        leadPaymentRepository.delete(payment);

        leadActivityRepository.save(activity(lead, ActivityType.PAYMENT_RECORDED,
                "Payment of ₹" + amount + " (" + type + ", " + status + ") deleted",
                currentUser));

        log.info("🗑️ Payment {} deleted by {} — ₹{} {}", paymentId, currentUser.getEmail(), amount, type);
        return ApiResponse.success("Payment deleted", "deleted");
    }

    // ─────────────────────────────────────────────
    // Dashboard stats
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<LeadStatsResponse> getStats() {
        long total           = leadRepository.count();
        long newLeads        = leadRepository.countByStatus(LeadStatus.NEW);
        long dnp             = leadRepository.countByStatusIn(List.of(
                LeadStatus.DNP_1, LeadStatus.DNP_2, LeadStatus.DNP_3,
                LeadStatus.DNP_4, LeadStatus.DNP_5));
        long whatsappSent     = leadRepository.countByStatus(LeadStatus.WHATSAPP_SENT);
        long whatsappResponded = leadRepository.countByStatus(LeadStatus.WHATSAPP_RESPONDED);
        long contacted        = leadRepository.countByStatus(LeadStatus.CONTACTED);
        long followup         = leadRepository.countByStatus(LeadStatus.FOLLOWUP_SCHEDULED);
        long demoBooked       = leadRepository.countByStatus(LeadStatus.DEMO_BOOKED);
        long demoDone         = leadRepository.countByStatus(LeadStatus.DEMO_DONE);
        long demoNoShow       = leadRepository.countByStatus(LeadStatus.DEMO_NO_SHOW);
        long closing          = leadRepository.countByStatus(LeadStatus.CLOSING);
        long paymentDone      = leadRepository.countByStatus(LeadStatus.PAYMENT_DONE);
        long notInterested    = leadRepository.countByStatus(LeadStatus.NOT_INTERESTED);
        long switchOff        = leadRepository.countByStatus(LeadStatus.SWITCH_OFF);

        String conversionRate = total > 0
                ? String.format("%.1f%%", (paymentDone * 100.0 / total))
                : "0.0%";

        LocalDateTime now        = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart  = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        long demosDoneToday   = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_DONE%", todayStart);
        long demosDoneWeek    = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_DONE%", weekStart);
        long demosDoneMonth   = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_DONE%", monthStart);
        long demoBookedToday  = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_BOOKED%", todayStart);
        long demoBookedWeek   = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_BOOKED%", weekStart);
        long demoBookedMonth  = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_BOOKED%", monthStart);

        long salesDoneWeek    = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to PAYMENT_DONE%", weekStart);
        long salesDoneMonth   = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to PAYMENT_DONE%", monthStart);

        long overdueFollowups = leadRepository.countOverdueFollowups(LeadStatus.FOLLOWUP_SCHEDULED, now);
        long dueSoonFollowups = leadRepository.countDueSoonFollowups(LeadStatus.FOLLOWUP_SCHEDULED, now, now.plusHours(2));

        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime fourDaysAgo  = now.minusDays(4);
        long agingLeads = leadRepository.countAgingLeads(TERMINAL_STATUSES, sevenDaysAgo, fourDaysAgo);
        long staleLeads = leadRepository.countStaleLeads(TERMINAL_STATUSES, sevenDaysAgo);

        return ApiResponse.success("Stats retrieved", LeadStatsResponse.builder()
                .total(total).newLeads(newLeads).dnp(dnp)
                .whatsappSent(whatsappSent).whatsappResponded(whatsappResponded)
                .contacted(contacted).followupScheduled(followup)
                .demoBooked(demoBooked).demoDone(demoDone).demoNoShow(demoNoShow)
                .closing(closing).paymentDone(paymentDone)
                .notInterested(notInterested).switchOff(switchOff)
                .conversionRate(conversionRate)
                .overdueFollowups(overdueFollowups).dueSoonFollowups(dueSoonFollowups)
                .agingLeads(agingLeads).staleLeads(staleLeads)
                .demosDoneToday(demosDoneToday).demosDoneThisWeek(demosDoneWeek).demosDoneThisMonth(demosDoneMonth)
                .demoBookedToday(demoBookedToday).demoBookedThisWeek(demoBookedWeek).demoBookedThisMonth(demoBookedMonth)
                .salesDoneThisWeek(salesDoneWeek).salesDoneThisMonth(salesDoneMonth)
                .build());
    }

    // ─────────────────────────────────────────────
    // Per-rep stats (admin dashboard)
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<SalesRepStatsResponse>> getRepStats() {
        List<User> reps = userRepository.findAllByUserRoleOrderByCreatedAtDesc(UserRole.SALES);
        // Also include ADMIN users who close deals
        List<User> admins = userRepository.findAllByUserRoleOrderByCreatedAtDesc(UserRole.ADMIN);
        List<User> all = new ArrayList<>(reps);
        all.addAll(admins);

        LocalDateTime weekStart  = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        List<SalesRepStatsResponse> stats = all.stream().map(rep -> {
            long activeLeads    = leadRepository.countByAssignedToIdAndStatusNotIn(rep.getId(), TERMINAL_STATUSES);
            long demosThisWeek  = leadActivityRepository.countStatusChangeByUserAfter(rep.getId(), ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_DONE%", weekStart);
            long demosThisMonth = leadActivityRepository.countStatusChangeByUserAfter(rep.getId(), ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_DONE%", monthStart);
            long salesThisWeek  = leadRepository.countByClosedByIdAndStatusAndUpdatedAtAfter(rep.getId(), LeadStatus.PAYMENT_DONE, weekStart);
            long salesThisMonth = leadRepository.countByClosedByIdAndStatusAndUpdatedAtAfter(rep.getId(), LeadStatus.PAYMENT_DONE, monthStart);
            long totalSales     = leadRepository.countByClosedByIdAndStatusAndUpdatedAtAfter(rep.getId(), LeadStatus.PAYMENT_DONE, LocalDateTime.of(2000, 1, 1, 0, 0));

            return SalesRepStatsResponse.builder()
                    .userId(rep.getId())
                    .repName(rep.getFirstName() + " " + rep.getLastName())
                    .activeLeads(activeLeads)
                    .demosThisWeek(demosThisWeek)
                    .demosThisMonth(demosThisMonth)
                    .salesThisWeek(salesThisWeek)
                    .salesThisMonth(salesThisMonth)
                    .totalSales(totalSales)
                    .build();
        }).collect(Collectors.toList());

        return ApiResponse.success("Rep stats retrieved", stats);
    }

    // ─────────────────────────────────────────────
    // Google Sheets sync
    // ─────────────────────────────────────────────

    public ApiResponse<LeadImportResponse> syncFromSheets(User currentUser) {
        log.info("🔄 Manual Sheets sync triggered by: {}", currentUser.getEmail());
        LeadImportResponse result = sheetSyncService.syncFromSheets(currentUser, "MANUAL");
        return ApiResponse.success("Sync complete", result);
    }

    public ApiResponse<List<SheetSyncLogResponse>> getSyncLogs() {
        List<SheetSyncLogResponse> logs = sheetSyncLogRepository.findTop20ByOrderBySyncedAtDesc()
                .stream().map(SheetSyncLogResponse::from).collect(Collectors.toList());
        return ApiResponse.success("Sync logs retrieved", logs);
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private void autoAssignIfUnassigned(Lead lead, User currentUser) {
        if (lead.getAssignedTo() == null && currentUser.getUserRole() == UserRole.SALES) {
            lead.setAssignedTo(currentUser);
            log.info("🤝 Lead {} auto-assigned to SALES user {}", lead.getId(), currentUser.getEmail());
        }
    }

    private LeadResponse buildFullResponse(Lead lead) {
        List<LeadActivityResponse> activities = leadActivityRepository
                .findByLeadIdOrderByCreatedAtAsc(lead.getId())
                .stream().map(LeadActivityResponse::from).collect(Collectors.toList());

        List<LeadPaymentResponse> payments = leadPaymentRepository
                .findByLeadIdOrderByCreatedAtAsc(lead.getId())
                .stream().map(LeadPaymentResponse::from).collect(Collectors.toList());

        BigDecimal totalPaid = leadPaymentRepository
                .sumAmountByLeadIdAndStatus(lead.getId(), PaymentStatus.PAID);

        return LeadResponse.from(lead, activities, payments, totalPaid);
    }

    private LeadActivity activity(Lead lead, ActivityType type, String description, User user) {
        return LeadActivity.builder()
                .lead(lead)
                .activityType(type)
                .description(description)
                .createdBy(user)
                .build();
    }

    private LeadStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return LeadStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
