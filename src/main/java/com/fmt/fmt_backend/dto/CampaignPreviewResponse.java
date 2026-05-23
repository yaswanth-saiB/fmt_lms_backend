package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CampaignPreviewResponse {
    private long leadCount;
    private String message;
}
