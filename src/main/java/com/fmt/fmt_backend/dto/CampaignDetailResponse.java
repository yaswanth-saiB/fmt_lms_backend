package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.CampaignStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CampaignDetailResponse {
    private UUID id;
    private String name;
    private String description;
    private String templateName;
    private List<String> templateParams;
    private String leadStatuses;
    private String courseInterest;
    private CampaignStatus status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private long deliveredCount;
    private long readCount;
    private long repliedCount;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private List<RecipientResponse> recipients;

    @Data
    @Builder
    public static class RecipientResponse {
        private UUID id;
        private UUID leadId;
        private String leadName;
        private String phone;
        private Boolean failed;
        private String errorMessage;
        private String waMessageId;
        private LocalDateTime sentAt;
        private String deliveryStatus;
        private Boolean replied;
        private LocalDateTime repliedAt;
    }
}
