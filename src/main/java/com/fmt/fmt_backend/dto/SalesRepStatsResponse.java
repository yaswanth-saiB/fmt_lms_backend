package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SalesRepStatsResponse {
    private UUID userId;
    private String repName;
    private long activeLeads;
    private long demosThisWeek;
    private long demosThisMonth;
    private long salesThisWeek;
    private long salesThisMonth;
    private long totalSales;
}
