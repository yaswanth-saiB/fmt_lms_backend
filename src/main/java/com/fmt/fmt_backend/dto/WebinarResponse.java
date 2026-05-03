package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmt.fmt_backend.entity.Webinar;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebinarResponse {

    private UUID id;
    private String title;
    private String description;
    private LocalDateTime scheduledAt;
    private String zoomLink;
    private String hostMentorName;
    private Boolean isActive;
    private Integer maxCapacity;
    private long registrationCount;
    private LocalDateTime createdAt;

    public static WebinarResponse from(Webinar w, long registrationCount) {
        String hostName = null;
        if (w.getHostMentor() != null) {
            hostName = w.getHostMentor().getFirstName() + " " + w.getHostMentor().getLastName();
        }
        return WebinarResponse.builder()
                .id(w.getId())
                .title(w.getTitle())
                .description(w.getDescription())
                .scheduledAt(w.getScheduledAt())
                .zoomLink(w.getZoomLink())
                .hostMentorName(hostName)
                .isActive(w.getIsActive())
                .maxCapacity(w.getMaxCapacity())
                .registrationCount(registrationCount)
                .createdAt(w.getCreatedAt())
                .build();
    }
}
