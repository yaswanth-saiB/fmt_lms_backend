package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.EnquiryRequest;
import com.fmt.fmt_backend.dto.EnquiryResponse;
import com.fmt.fmt_backend.entity.Enquiry;
import com.fmt.fmt_backend.repository.EnquiryRepository;
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
    private final SendGridEmailService emailService;
    private final HttpServletRequest request;

    @Transactional
    public EnquiryResponse submitEnquiry(EnquiryRequest enquiryRequest) {
        log.info("📋 New enquiry from: {} - {}", enquiryRequest.getName(), enquiryRequest.getMobile());

        Enquiry enquiry = Enquiry.builder()
                .name(enquiryRequest.getName())
                .mobile(enquiryRequest.getMobile())
                .city(enquiryRequest.getCity())
                .experienceLevel(enquiryRequest.getExperienceLevel())
                .areaOfInterest(enquiryRequest.getAreaOfInterest())
                .message(enquiryRequest.getMessage())
                .status(Enquiry.EnquiryStatus.NEW)
                .ipAddress(getClientIp())
                .userAgent(request.getHeader("User-Agent"))
                .build();

        Enquiry savedEnquiry = enquiryRepository.save(enquiry);
        log.info("✅ Enquiry saved with ID: {}", savedEnquiry.getId());

        try {
            emailService.sendEnquiryNotification(savedEnquiry);
            log.info("📧 Enquiry notification email sent");
        } catch (Exception e) {
            log.error("❌ Failed to send enquiry email: {}", e.getMessage());
        }

        return mapToResponse(savedEnquiry);
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