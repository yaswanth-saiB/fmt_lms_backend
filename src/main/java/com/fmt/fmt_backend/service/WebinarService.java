package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.*;
import com.fmt.fmt_backend.enums.ActivityType;
import com.fmt.fmt_backend.enums.LeadSource;
import com.fmt.fmt_backend.enums.LeadStatus;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebinarService {

    private final WebinarRepository webinarRepository;
    private final WebinarRegistrationRepository registrationRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────
    // Admin — CRUD
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<WebinarResponse> createWebinar(WebinarRequest request, User currentUser) {
        Webinar.WebinarBuilder builder = Webinar.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .scheduledAt(request.getScheduledAt())
                .zoomLink(request.getZoomLink())
                .maxCapacity(request.getMaxCapacity());

        if (request.getHostMentorId() != null) {
            userRepository.findById(request.getHostMentorId()).ifPresent(builder::hostMentor);
        }

        Webinar webinar = webinarRepository.save(builder.build());
        log.info("📅 Webinar created: {} by {}", webinar.getTitle(), currentUser.getEmail());
        return ApiResponse.success("Webinar created", toResponse(webinar));
    }

    @Transactional
    public ApiResponse<WebinarResponse> updateWebinar(UUID id, WebinarRequest request) {
        Optional<Webinar> opt = webinarRepository.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("Webinar not found");

        Webinar webinar = opt.get();
        webinar.setTitle(request.getTitle());
        webinar.setDescription(request.getDescription());
        webinar.setScheduledAt(request.getScheduledAt());
        webinar.setZoomLink(request.getZoomLink());
        webinar.setMaxCapacity(request.getMaxCapacity());

        if (request.getHostMentorId() != null) {
            userRepository.findById(request.getHostMentorId()).ifPresent(webinar::setHostMentor);
        }

        webinar = webinarRepository.save(webinar);
        return ApiResponse.success("Webinar updated", toResponse(webinar));
    }

    @Transactional
    public ApiResponse<WebinarResponse> toggleActive(UUID id) {
        Optional<Webinar> opt = webinarRepository.findById(id);
        if (opt.isEmpty()) return ApiResponse.error("Webinar not found");

        Webinar webinar = opt.get();
        webinar.setIsActive(!Boolean.TRUE.equals(webinar.getIsActive()));
        webinar = webinarRepository.save(webinar);
        String msg = Boolean.TRUE.equals(webinar.getIsActive()) ? "Webinar activated" : "Webinar deactivated";
        return ApiResponse.success(msg, toResponse(webinar));
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<WebinarResponse>> getAllWebinars() {
        List<WebinarResponse> list = webinarRepository.findAllByOrderByScheduledAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ApiResponse.success("Webinars retrieved", list);
    }

    @Transactional(readOnly = true)
    public ApiResponse<WebinarResponse> getWebinarById(UUID id) {
        return webinarRepository.findById(id)
                .map(w -> ApiResponse.success("Webinar found", toResponse(w)))
                .orElse(ApiResponse.error("Webinar not found"));
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<WebinarResponse>> getActiveWebinars() {
        List<WebinarResponse> list = webinarRepository.findAllByIsActiveTrueOrderByScheduledAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ApiResponse.success("Active webinars retrieved", list);
    }

    // ─────────────────────────────────────────────
    // Public registration
    // ─────────────────────────────────────────────

    @Transactional
    public ApiResponse<WebinarRegistrationResponse> register(UUID webinarId, WebinarRegistrationRequest request) {
        Optional<Webinar> opt = webinarRepository.findById(webinarId);
        if (opt.isEmpty()) return ApiResponse.error("Webinar not found");

        Webinar webinar = opt.get();
        if (!Boolean.TRUE.equals(webinar.getIsActive())) {
            return ApiResponse.error("Registration is closed for this webinar");
        }

        // Normalise phone
        String phone = normalisePhone(request.getPhone());
        if (phone.isBlank()) return ApiResponse.error("Invalid phone number");

        if (registrationRepository.existsByWebinarIdAndPhone(webinarId, phone)) {
            return ApiResponse.error("You are already registered for this webinar");
        }

        // Capacity check
        if (webinar.getMaxCapacity() != null) {
            long count = registrationRepository.countByWebinarId(webinarId);
            if (count >= webinar.getMaxCapacity()) {
                return ApiResponse.error("Webinar is full. Registration closed.");
            }
        }

        // Auto-create lead if this phone is new
        Lead lead = leadRepository.findByPhone(phone).orElse(null);
        if (lead == null) {
            lead = Lead.builder()
                    .name(request.getName())
                    .phone(phone)
                    .email(request.getEmail())
                    .source(LeadSource.WEBINAR)
                    .status(LeadStatus.NEW)
                    .build();
            lead = leadRepository.save(lead);

            leadActivityRepository.save(LeadActivity.builder()
                    .lead(lead)
                    .activityType(ActivityType.WEBINAR_REGISTERED)
                    .description("Registered for webinar: " + webinar.getTitle())
                    .build());

            log.info("🆕 Lead auto-created from webinar registration — phone: {}", phone);
        }

        WebinarRegistration reg = WebinarRegistration.builder()
                .webinar(webinar)
                .lead(lead)
                .name(request.getName())
                .phone(phone)
                .email(request.getEmail())
                .utmSource(request.getUtmSource())
                .utmMedium(request.getUtmMedium())
                .utmCampaign(request.getUtmCampaign())
                .build();

        reg = registrationRepository.save(reg);

        // Increment registration count
        webinar.setRegistrationCount(webinar.getRegistrationCount() + 1);
        webinarRepository.save(webinar);

        log.info("✅ Webinar registration complete — {} for {}", phone, webinar.getTitle());
        return ApiResponse.success("Registered successfully", WebinarRegistrationResponse.from(reg));
    }

    // ─────────────────────────────────────────────
    // Admin — view registrations + mark attended
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<WebinarRegistrationResponse>> getRegistrations(UUID webinarId) {
        if (!webinarRepository.existsById(webinarId)) return ApiResponse.error("Webinar not found");

        List<WebinarRegistrationResponse> list = registrationRepository
                .findByWebinarIdOrderByRegisteredAtDesc(webinarId)
                .stream().map(WebinarRegistrationResponse::from).collect(Collectors.toList());
        return ApiResponse.success("Registrations retrieved", list);
    }

    @Transactional
    public ApiResponse<WebinarRegistrationResponse> markAttended(UUID webinarId, UUID registrationId) {
        Optional<WebinarRegistration> opt = registrationRepository.findById(registrationId);
        if (opt.isEmpty()) return ApiResponse.error("Registration not found");

        WebinarRegistration reg = opt.get();
        if (!reg.getWebinar().getId().equals(webinarId)) {
            return ApiResponse.error("Registration does not belong to this webinar");
        }

        reg.setAttended(true);
        reg = registrationRepository.save(reg);
        return ApiResponse.success("Marked as attended", WebinarRegistrationResponse.from(reg));
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private WebinarResponse toResponse(Webinar w) {
        long count = registrationRepository.countByWebinarId(w.getId());
        return WebinarResponse.from(w, count);
    }

    private String normalisePhone(String raw) {
        if (raw == null) return "";
        String phone = raw.replaceAll("[^0-9+]", "");
        if (phone.startsWith("91") && phone.length() == 12) phone = phone.substring(2);
        if (phone.startsWith("+91") && phone.length() == 13) phone = phone.substring(3);
        return phone;
    }
}
