package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.LeadStatus;
import lombok.Data;

import java.util.List;

@Data
public class CampaignRequest {
    private String name;
    private String description;
    private String templateName;
    private List<String> templateParams;
    private List<String> templateParamNames; // variable names parallel to templateParams; null entry = positional
    private List<LeadStatus> leadStatuses;   // null/empty = all statuses
    private String courseInterest;           // null/blank = all course interests
}
