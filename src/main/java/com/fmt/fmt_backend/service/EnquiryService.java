package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.EnquiryRequest;
import com.fmt.fmt_backend.dto.EnquiryResponse;
import com.fmt.fmt_backend.entity.Enquiry;
import com.fmt.fmt_backend.repository.EnquiryRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor  // ✅ This works with constructor injection
@Slf4j
public class EnquiryService {

    private final EnquiryRepository enquiryRepository;
    private final ResendEmailService emailService;
    private final HttpServletRequest request;

    public EnquiryResponse submitEnquiry(EnquiryRequest enquiryRequest) {
        log.info("📋 New enquiry from: {} - {}", enquiryRequest.getName(), enquiryRequest.getMobile());

        String rawUserAgent = request.getHeader("User-Agent");
        String userAgent = rawUserAgent != null && rawUserAgent.length() > 500
                ? rawUserAgent.substring(0, 500)
                : rawUserAgent;

        Enquiry enquiry = Enquiry.builder()
                .name(enquiryRequest.getName())
                .mobile(enquiryRequest.getMobile())
                .city(enquiryRequest.getCity())
                .experienceLevel(enquiryRequest.getExperienceLevel())
                .areaOfInterest(enquiryRequest.getAreaOfInterest())
                .message(enquiryRequest.getMessage())
                .status(Enquiry.EnquiryStatus.NEW)
                .ipAddress(getClientIp())
                .userAgent(userAgent)
                .build();

        try {
            Enquiry savedEnquiry = enquiryRepository.save(enquiry);
            log.info("✅ Enquiry saved with ID: {}", savedEnquiry.getId());
            emailService.sendEnquiryNotification(savedEnquiry);
            return mapToResponse(savedEnquiry);
        } catch (Exception e) {
            log.error("❌ Enquiry save failed for {} ({}): {}", enquiryRequest.getName(), enquiryRequest.getMobile(), e.getMessage());
            emailService.sendEnquiryFailureAlert(enquiryRequest, getClientIp(), e.getMessage());
            // Return gracefully so the user doesn't see an error and retry-spam
            return EnquiryResponse.builder()
                    .name(enquiryRequest.getName())
                    .mobile(enquiryRequest.getMobile())
                    .city(enquiryRequest.getCity())
                    .experienceLevel(enquiryRequest.getExperienceLevel() != null ? enquiryRequest.getExperienceLevel().name() : null)
                    .areaOfInterest(enquiryRequest.getAreaOfInterest())
                    .message(enquiryRequest.getMessage())
                    .status(Enquiry.EnquiryStatus.NEW.name())
                    .build();
        }
    }

    public List<EnquiryResponse> getAll(Enquiry.EnquiryStatus status) {
        List<Enquiry> enquiries = (status != null)
                ? enquiryRepository.findByStatusOrderByCreatedAtDesc(status)
                : enquiryRepository.findAllByOrderByCreatedAtDesc();
        return enquiries.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public EnquiryResponse getById(UUID id) {
        Enquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Enquiry not found"));
        return mapToResponse(enquiry);
    }

    @Transactional
    public EnquiryResponse updateStatus(UUID id, Enquiry.EnquiryStatus newStatus) {
        Enquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Enquiry not found"));
        enquiry.setStatus(newStatus);
        return mapToResponse(enquiryRepository.save(enquiry));
    }

    private String getClientIp() {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private EnquiryResponse mapToResponse(Enquiry enquiry) {
        return EnquiryResponse.builder()
                .id(enquiry.getId())
                .name(enquiry.getName())
                .mobile(enquiry.getMobile())
                .city(enquiry.getCity())
                .experienceLevel(enquiry.getExperienceLevel() != null ?
                        enquiry.getExperienceLevel().name() : null)
                .areaOfInterest(enquiry.getAreaOfInterest())
                .message(enquiry.getMessage())
                .status(enquiry.getStatus().name())
                .createdAt(enquiry.getCreatedAt())
                .build();
    }
}