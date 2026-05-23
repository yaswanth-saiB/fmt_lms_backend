package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.CampaignStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CampaignSummaryResponse {
    private UUID id;
    private String name;
    private String description;
    private String templateName;
    private String leadStatuses;
    private String courseInterest;
    private CampaignStatus status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
