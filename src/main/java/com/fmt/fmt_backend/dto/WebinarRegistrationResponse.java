package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmt.fmt_backend.entity.WebinarRegistration;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebinarRegistrationResponse {

    private UUID id;
    private UUID webinarId;
    private String webinarTitle;
    private UUID leadId;
    private String name;
    private String phone;
    private String email;
    private LocalDateTime registeredAt;
    private Boolean attended;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;

    public static WebinarRegistrationResponse from(WebinarRegistration r) {
        return WebinarRegistrationResponse.builder()
                .id(r.getId())
                .webinarId(r.getWebinar().getId())
                .webinarTitle(r.getWebinar().getTitle())
                .leadId(r.getLead() != null ? r.getLead().getId() : null)
                .name(r.getName())
                .phone(r.getPhone())
                .email(r.getEmail())
                .registeredAt(r.getRegisteredAt())
                .attended(r.getAttended())
                .utmSource(r.getUtmSource())
                .utmMedium(r.getUtmMedium())
                .utmCampaign(r.getUtmCampaign())
                .build();
    }
}
