package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.LeadImportResponse;
import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.entity.LeadActivity;
import com.fmt.fmt_backend.entity.SheetSyncLog;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.ActivityType;
import com.fmt.fmt_backend.enums.LeadSource;
import com.fmt.fmt_backend.enums.LeadStatus;
import com.fmt.fmt_backend.enums.PreferredTiming;
import com.fmt.fmt_backend.repository.LeadActivityRepository;
import com.fmt.fmt_backend.repository.LeadRepository;
import com.fmt.fmt_backend.repository.SheetSyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SheetSyncService {

    private final GoogleSheetsService googleSheetsService;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final SheetSyncLogRepository sheetSyncLogRepository;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,   // 2026-04-09T23:17:57+05:30
            DateTimeFormatter.ofPattern("M-d-yy"),    // 4-10-26
            DateTimeFormatter.ofPattern("d-M-yy")     // in case day-first variant appears
    );

    @Transactional
    public LeadImportResponse syncFromSheets(User triggeredBy, String triggerType) {
        log.info("🔄 Google Sheets sync starting — trigger: {}", triggerType);

        int imported = 0;
        int duplicates = 0;
        int blankPhone = 0;
        List<String> errors = new ArrayList<>();
        int sheetRowsRead = 0;

        try {
            List<List<Object>> allRows = googleSheetsService.readAllRows();

            if (allRows.size() < 2) {
                log.info("📄 Sheet has no data rows");
                saveLog(sheetRowsRead, imported, 0, triggerType, triggeredBy, errors);
                return LeadImportResponse.builder().imported(0).skipped(0).duplicates(0).blankPhone(0).errors(errors).build();
            }

            // Build header index map from row 0
            List<Object> headerRow = allRows.get(0);
            Map<String, Integer> headerMap = buildHeaderMap(headerRow);
            List<Integer> statusIndices = findAllStatusIndices(headerRow);

            Integer createdTimeIdx = findColumn(headerMap, "created_time", "timestamp", "date");
            Integer platformIdx   = findColumn(headerMap, "platform", "source", "lead source");
            Integer levelIdx      = findColumn(headerMap, "what_is_your_current_level_in_stock_market?", "current level", "level");
            Integer modeIdx       = findColumn(headerMap, "preferred_learning_mode", "learning mode", "mode");
            Integer nameIdx       = findColumn(headerMap, "full_name", "name", "full name");
            Integer phoneIdx      = findColumn(headerMap, "phone_number", "phone", "mobile", "contact");
            Integer emailIdx      = findColumn(headerMap, "email", "email address");
            Integer commentIdx    = findColumn(headerMap, "comment", "comments", "remark", "remarks");
            Integer timingsIdx   = findColumn(headerMap, "preferred_timings", "preferred timing", "preferred_timing", "timings", "timing");

            if (phoneIdx == null) {
                errors.add("Phone column (phone_number / phone / mobile) not found in sheet headers");
                saveLog(0, 0, 0, triggerType, triggeredBy, errors);
                return LeadImportResponse.builder().imported(0).skipped(0).errors(errors).build();
            }

            sheetRowsRead = allRows.size() - 1; // exclude header

            for (int i = 1; i < allRows.size(); i++) {
                List<Object> row = allRows.get(i);

                try {
                    String phone = cell(row, phoneIdx).replaceAll("[^0-9+]", "");
                    if (phone.isBlank()) {
                        blankPhone++;
                        errors.add("Row " + (i + 1) + " — blank phone (skipped)");
                        continue;
                    }

                    // Normalise: strip country code
                    if (phone.startsWith("91") && phone.length() == 12) phone = phone.substring(2);
                    if (phone.startsWith("+91") && phone.length() == 13) phone = phone.substring(3);

                    if (leadRepository.existsByPhone(phone)) {
                        duplicates++;
                        errors.add("Row " + (i + 1) + " — duplicate: " + phone + " (already in CRM)");
                        continue;
                    }

                    String name = nameIdx != null ? blank(cell(row, nameIdx)) : null;

                    String email    = emailIdx != null ? blank(cell(row, emailIdx)) : null;
                    String platform = platformIdx != null ? cell(row, platformIdx) : null;
                    String level    = levelIdx != null ? blank(cell(row, levelIdx)) : null;
                    String mode     = modeIdx != null ? blank(cell(row, modeIdx)) : null;
                    String notes    = buildNotes(row, statusIndices, commentIdx);
                    PreferredTiming timing = timingsIdx != null ? mapTiming(cell(row, timingsIdx)) : null;
                    LocalDateTime sheetCreatedAt = createdTimeIdx != null
                            ? parseDateTime(cell(row, createdTimeIdx)) : null;

                    Lead lead = Lead.builder()
                            .name(name)
                            .phone(phone)
                            .email(email)
                            .source(mapSource(platform))
                            .status(LeadStatus.IMPORTED)
                            .currentLevel(level)
                            .preferredLearningMode(mode)
                            .preferredTimings(timing)
                            .notes(notes)
                            .sheetCreatedAt(sheetCreatedAt)
                            .build();

                    lead = leadRepository.save(lead);

                    leadActivityRepository.save(LeadActivity.builder()
                            .lead(lead)
                            .activityType(ActivityType.LEAD_IMPORTED)
                            .description("Lead synced from Google Sheet (FMT Ad Leads)")
                            .createdBy(triggeredBy)
                            .build());

                    imported++;

                } catch (Exception e) {
                    errors.add("Row " + (i + 1) + ": " + e.getMessage());
                    log.warn("⚠ Sheet row {} failed: {}", i + 1, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Google Sheets sync failed: {}", e.getMessage(), e);
            errors.add("Sync failed: " + e.getMessage());
        }

        saveLog(sheetRowsRead, imported, duplicates + blankPhone, triggerType, triggeredBy, errors);
        log.info("✅ Sheet sync done — imported: {}, duplicates: {}, blankPhone: {}, errors: {}", imported, duplicates, blankPhone, errors.size());
        return LeadImportResponse.builder()
                .imported(imported)
                .skipped(duplicates + blankPhone)
                .duplicates(duplicates)
                .blankPhone(blankPhone)
                .errors(errors)
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void saveLog(int rowsRead, int imported, int skipped, String triggerType,
                         User triggeredBy, List<String> errors) {
        String errorsText = errors.isEmpty() ? null : String.join("\n", errors);
        sheetSyncLogRepository.save(SheetSyncLog.builder()
                .sheetRowsRead(rowsRead)
                .importedCount(imported)
                .skippedCount(skipped)
                .triggerType(triggerType)
                .triggeredBy(triggeredBy)
                .errors(errorsText)
                .build());
    }

    private Map<String, Integer> buildHeaderMap(List<Object> headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String header = headerRow.get(i).toString().toLowerCase().trim();
            // Put only first occurrence (avoids overwriting duplicate "Status" with same key)
            map.putIfAbsent(header, i);
        }
        return map;
    }

    private List<Integer> findAllStatusIndices(List<Object> headerRow) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < headerRow.size(); i++) {
            if ("status".equalsIgnoreCase(headerRow.get(i).toString().trim())) {
                indices.add(i);
            }
        }
        return indices;
    }

    private Integer findColumn(Map<String, Integer> headerMap, String... candidates) {
        for (String candidate : candidates) {
            if (headerMap.containsKey(candidate.toLowerCase())) {
                return headerMap.get(candidate.toLowerCase());
            }
        }
        return null;
    }

    private String cell(List<Object> row, int index) {
        if (index >= row.size() || row.get(index) == null) return "";
        return row.get(index).toString().trim();
    }

    private String blank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String buildNotes(List<Object> row, List<Integer> statusIndices, Integer commentIdx) {
        List<String> parts = new ArrayList<>();

        List<String> statusVals = new ArrayList<>();
        for (int idx : statusIndices) {
            String val = cell(row, idx);
            if (!val.isBlank()) statusVals.add(val);
        }
        if (!statusVals.isEmpty()) {
            parts.add("[Sheet Status: " + String.join(" | ", statusVals) + "]");
        }

        if (commentIdx != null) {
            String comment = cell(row, commentIdx);
            if (!comment.isBlank()) parts.add(comment);
        }

        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                // OffsetDateTime (e.g. 2026-04-09T23:17:57+05:30) — convert to LocalDateTime
                try {
                    return java.time.OffsetDateTime.parse(trimmed, fmt).toLocalDateTime();
                } catch (Exception ignored) {}
                // Date-only formats (e.g. 4-10-26) — use start of day
                try {
                    return java.time.LocalDate.parse(trimmed, fmt).atStartOfDay();
                } catch (Exception ignored) {}
                return LocalDateTime.parse(trimmed, fmt);
            } catch (Exception ignored) {}
        }
        log.debug("⚠ Could not parse sheet datetime: {}", value);
        return null;
    }

    private PreferredTiming mapTiming(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.toLowerCase().replaceAll("[_\\s-]", "");
        return switch (v) {
            case "morning" -> PreferredTiming.MORNING;
            case "afternoon" -> PreferredTiming.AFTERNOON;
            case "evening" -> PreferredTiming.EVENING;
            case "night" -> PreferredTiming.NIGHT;
            default -> null;
        };
    }

    private LeadSource mapSource(String platform) {
        if (platform == null || platform.isBlank()) return LeadSource.META_ADS;
        String p = platform.toLowerCase();
        if (p.contains("meta") || p.contains("facebook") || p.contains("fb") || p.contains("instagram") || p.contains("ig")) return LeadSource.META_ADS;
        if (p.contains("google")) return LeadSource.GOOGLE_ADS;
        if (p.contains("organic") || p.contains("seo")) return LeadSource.ORGANIC;
        if (p.contains("referral") || p.contains("refer")) return LeadSource.REFERRAL;
        if (p.contains("walk")) return LeadSource.WALK_IN;
        return LeadSource.OTHER;
    }
}
