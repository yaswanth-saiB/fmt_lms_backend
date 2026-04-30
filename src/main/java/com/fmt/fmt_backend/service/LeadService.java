package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.entity.LeadActivity;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.ActivityType;
import com.fmt.fmt_backend.enums.LeadSource;
import com.fmt.fmt_backend.enums.LeadStatus;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.LeadActivityRepository;
import com.fmt.fmt_backend.repository.LeadRepository;
import com.fmt.fmt_backend.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final ExcelImportService excelImportService;

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

        leadActivityRepository.save(activity(lead, ActivityType.LEAD_IMPORTED,
                "Lead added manually", currentUser));

        return ApiResponse.success("Lead created", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Paginated list with filters
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getLeads(
            String status, UUID assignedTo, String search, int page, int size) {

        LeadStatus statusEnum = parseStatus(status);

        Specification<Lead> spec = Specification.where(null);

        if (statusEnum != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusEnum));
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
    // Single lead detail with activities
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

        lead = leadRepository.save(lead);

        leadActivityRepository.save(activity(lead, ActivityType.CALL_ATTEMPTED,
                "DNP " + newCount + " - Called, no answer", currentUser));

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
        lead = leadRepository.save(lead);

        leadActivityRepository.save(activity(lead, ActivityType.WHATSAPP_SENT,
                "WhatsApp sent after 5 failed call attempts", currentUser));

        return ApiResponse.success("WhatsApp marked as sent", buildFullResponse(lead));
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
        lead = leadRepository.save(lead);

        leadActivityRepository.save(activity(lead, ActivityType.NOTE_ADDED,
                "Note: " + request.getNote(), currentUser));

        return ApiResponse.success("Note added", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Assign lead (ADMIN only — enforced in SecurityConfig)
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
                "Lead assigned to " + assignee.getFirstName() + " " + assignee.getLastName(),
                currentUser));

        return ApiResponse.success("Lead assigned", buildFullResponse(lead));
    }

    // ─────────────────────────────────────────────
    // Dashboard stats
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<LeadStatsResponse> getStats() {
        long total        = leadRepository.count();
        long newLeads     = leadRepository.countByStatus(LeadStatus.NEW);
        long dnp          = leadRepository.countByStatusIn(List.of(
                LeadStatus.DNP_1, LeadStatus.DNP_2, LeadStatus.DNP_3,
                LeadStatus.DNP_4, LeadStatus.DNP_5));
        long whatsappSent = leadRepository.countByStatus(LeadStatus.WHATSAPP_SENT);
        long contacted    = leadRepository.countByStatus(LeadStatus.CONTACTED);
        long followup     = leadRepository.countByStatus(LeadStatus.FOLLOWUP_SCHEDULED);
        long demoBooked   = leadRepository.countByStatus(LeadStatus.DEMO_BOOKED);
        long demoDone     = leadRepository.countByStatus(LeadStatus.DEMO_DONE);
        long closing      = leadRepository.countByStatus(LeadStatus.CLOSING);
        long paymentDone  = leadRepository.countByStatus(LeadStatus.PAYMENT_DONE);
        long notInterested = leadRepository.countByStatus(LeadStatus.NOT_INTERESTED);
        long switchOff    = leadRepository.countByStatus(LeadStatus.SWITCH_OFF);

        String conversionRate = total > 0
                ? String.format("%.1f%%", (paymentDone * 100.0 / total))
                : "0.0%";

        // Activity-based demo counts (accurate timestamps regardless of future status changes)
        LocalDateTime todayStart  = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart   = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime monthStart  = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        long demosDoneToday    = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_DONE%", todayStart);
        long demosDoneWeek     = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_DONE%", weekStart);
        long demosDoneMonth    = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_DONE%", monthStart);
        long demoBookedToday   = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_BOOKED%", todayStart);
        long demoBookedWeek    = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_BOOKED%", weekStart);
        long demoBookedMonth   = leadActivityRepository.countStatusChangeAfter(ActivityType.STATUS_CHANGE, "Status changed from%to DEMO_BOOKED%", monthStart);

        long overdueFollowups  = leadRepository.countOverdueFollowups(LeadStatus.FOLLOWUP_SCHEDULED, LocalDateTime.now());

        LeadStatsResponse stats = LeadStatsResponse.builder()
                .total(total)
                .newLeads(newLeads)
                .dnp(dnp)
                .whatsappSent(whatsappSent)
                .contacted(contacted)
                .followupScheduled(followup)
                .demoBooked(demoBooked)
                .demoDone(demoDone)
                .closing(closing)
                .paymentDone(paymentDone)
                .notInterested(notInterested)
                .switchOff(switchOff)
                .conversionRate(conversionRate)
                .demosDoneToday(demosDoneToday)
                .demosDoneThisWeek(demosDoneWeek)
                .demosDoneThisMonth(demosDoneMonth)
                .demoBookedToday(demoBookedToday)
                .demoBookedThisWeek(demoBookedWeek)
                .demoBookedThisMonth(demoBookedMonth)
                .overdueFollowups(overdueFollowups)
                .build();

        return ApiResponse.success("Stats retrieved", stats);
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private LeadResponse buildFullResponse(Lead lead) {
        List<LeadActivityResponse> activities = leadActivityRepository
                .findByLeadIdOrderByCreatedAtAsc(lead.getId())
                .stream()
                .map(LeadActivityResponse::from)
                .collect(Collectors.toList());
        return LeadResponse.from(lead, activities);
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
