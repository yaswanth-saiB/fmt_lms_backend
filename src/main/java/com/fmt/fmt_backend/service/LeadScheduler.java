package com.fmt.fmt_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeadScheduler {

    private final SheetSyncService sheetSyncService;

    // Mon–Sat at 10:00 AM IST
    @Scheduled(cron = "0 0 10 * * MON-SAT", zone = "Asia/Kolkata")
    public void morningSync() {
        log.info("⏰ Morning Sheets sync triggered (10 AM IST)");
        sheetSyncService.syncFromSheets(null, "SCHEDULED");
    }

    // Mon–Sat at 5:00 PM IST
    @Scheduled(cron = "0 0 17 * * MON-SAT", zone = "Asia/Kolkata")
    public void eveningSync() {
        log.info("⏰ Evening Sheets sync triggered (5 PM IST)");
        sheetSyncService.syncFromSheets(null, "SCHEDULED");
    }
}
