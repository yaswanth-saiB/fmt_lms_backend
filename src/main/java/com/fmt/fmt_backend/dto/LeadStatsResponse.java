package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeadStatsResponse {

    // Status counts
    private long total;
    private long newLeads;
    private long dnp;
    private long whatsappSent;
    private long whatsappResponded;
    private long contacted;
    private long followupScheduled;
    private long demoBooked;
    private long demoDone;
    private long demoNoShow;
    private long closing;
    private long paymentDone;
    private long notInterested;
    private long switchOff;
    private String conversionRate;

    // Follow-up alerts
    private long overdueFollowups;    // past their scheduled time
    private long dueSoonFollowups;    // due in the next 2 hours

    // Lead health — age-based
    private long agingLeads;          // 4–7 days old, still active
    private long staleLeads;          // 8+ days old, still active

    // Demo activity (from activity log — accurate timestamps)
    private long demosDoneToday;
    private long demosDoneThisWeek;
    private long demosDoneThisMonth;
    private long demoBookedToday;
    private long demoBookedThisWeek;
    private long demoBookedThisMonth;

    // Sales activity
    private long salesDoneThisWeek;
    private long salesDoneThisMonth;
}
