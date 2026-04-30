package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeadStatsResponse {

    private long total;
    private long newLeads;
    private long dnp;
    private long whatsappSent;
    private long contacted;
    private long followupScheduled;
    private long demoBooked;
    private long demoDone;
    private long closing;
    private long paymentDone;
    private long notInterested;
    private long switchOff;
    private String conversionRate;

    // Demo activity breakdowns (tracked via activity log timestamps)
    private long demosDoneToday;
    private long demosDoneThisWeek;
    private long demosDoneThisMonth;
    private long demoBookedToday;
    private long demoBookedThisWeek;
    private long demoBookedThisMonth;

    private long overdueFollowups;
}
