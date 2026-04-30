package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmt.fmt_backend.entity.LeadActivity;
import com.fmt.fmt_backend.enums.ActivityType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadActivityResponse {

    private UUID id;
    private ActivityType activityType;
    private String description;
    private String createdByName;
    private LocalDateTime createdAt;

    public static LeadActivityResponse from(LeadActivity activity) {
        String createdByName = null;
        if (activity.getCreatedBy() != null) {
            createdByName = activity.getCreatedBy().getFirstName() + " " + activity.getCreatedBy().getLastName();
        }
        return LeadActivityResponse.builder()
                .id(activity.getId())
                .activityType(activity.getActivityType())
                .description(activity.getDescription())
                .createdByName(createdByName)
                .createdAt(activity.getCreatedAt())
                .build();
    }
}
