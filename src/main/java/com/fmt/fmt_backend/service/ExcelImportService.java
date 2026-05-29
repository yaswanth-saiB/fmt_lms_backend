package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.LeadImportResponse;
import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.entity.LeadActivity;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.ActivityType;
import com.fmt.fmt_backend.enums.LeadSource;
import com.fmt.fmt_backend.enums.LeadStatus;
import com.fmt.fmt_backend.repository.LeadActivityRepository;
import com.fmt.fmt_backend.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;

    @Transactional
    public LeadImportResponse importLeads(MultipartFile file, User importedBy) throws IOException {
        log.info("📥 Starting Excel import by user: {}", importedBy.getEmail());

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getPhysicalNumberOfRows() < 2) {
                errors.add("Excel file is empty or has only a header row.");
                return LeadImportResponse.builder()
                        .imported(0).skipped(0).errors(errors).build();
            }

            // Build header → column index map from row 0
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> headerMap = buildHeaderMap(headerRow);

            Integer nameIdx    = findColumn(headerMap, "full_name", "name", "full name", "customer name", "lead name");
            Integer phoneIdx   = findColumn(headerMap, "phone_number", "phone", "mobile", "contact", "mobile number", "contact number");
            Integer emailIdx   = findColumn(headerMap, "email", "email address", "email id");
            Integer platformIdx = findColumn(headerMap, "platform", "source", "lead source", "channel");
            Integer levelIdx   = findColumn(headerMap, "what_is_your_current_level_in_stock_market?", "current level", "level", "experience", "stock market level");
            Integer modeIdx    = findColumn(headerMap, "preferred_learning_mode", "learning mode", "mode", "preferred mode");
            Integer courseIdx  = findColumn(headerMap, "course_interest", "course", "interest", "course interest");
            Integer commentIdx = findColumn(headerMap, "comment", "comments", "notes", "remark", "remarks");

            if (phoneIdx == null) {
                errors.add("Required column 'phone_number' (or 'phone'/'mobile') not found in the Excel file.");
                return LeadImportResponse.builder()
                        .imported(0).skipped(0).errors(errors).build();
            }

            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    skipped++;
                    continue;
                }

                try {
                    String phone = getCellValue(row, phoneIdx).replaceAll("[^0-9+]", "");
                    if (phone.isBlank()) {
                        skipped++;
                        continue;
                    }

                    // Normalize: strip leading country code for 12-digit numbers starting with 91
                    if (phone.startsWith("91") && phone.length() == 12) {
                        phone = phone.substring(2);
                    }
                    if (phone.startsWith("+91") && phone.length() == 13) {
                        phone = phone.substring(3);
                    }

                    if (leadRepository.existsByPhone(phone)) {
                        skipped++;
                        log.debug("⏭ Skipped duplicate phone: {}", phone);
                        continue;
                    }

                    String name = nameIdx != null ? nullIfBlank(getCellValue(row, nameIdx)) : null;

                    String email       = emailIdx != null ? getCellValue(row, emailIdx) : null;
                    String platform    = platformIdx != null ? getCellValue(row, platformIdx) : null;
                    String level       = levelIdx != null ? getCellValue(row, levelIdx) : null;
                    String mode        = modeIdx != null ? getCellValue(row, modeIdx) : null;
                    String course      = courseIdx != null ? getCellValue(row, courseIdx) : null;
                    String comment     = commentIdx != null ? getCellValue(row, commentIdx) : null;

                    Lead lead = Lead.builder()
                            .name(name)
                            .phone(phone)
                            .email(nullIfBlank(email))
                            .courseInterest(nullIfBlank(course))
                            .source(mapPlatformToSource(platform))
                            .status(LeadStatus.IMPORTED)
                            .currentLevel(nullIfBlank(level))
                            .preferredLearningMode(nullIfBlank(mode))
                            .notes(nullIfBlank(comment))
                            .build();

                    lead = leadRepository.save(lead);

                    leadActivityRepository.save(LeadActivity.builder()
                            .lead(lead)
                            .activityType(ActivityType.LEAD_IMPORTED)
                            .description("Lead imported from Excel")
                            .createdBy(importedBy)
                            .build());

                    imported++;
                } catch (Exception e) {
                    errors.add("Row " + (rowNum + 1) + ": " + e.getMessage());
                    log.warn("⚠ Excel import row {} failed: {}", rowNum + 1, e.getMessage());
                }
            }
        }

        log.info("✅ Excel import done — imported: {}, skipped: {}, errors: {}", imported, skipped, errors.size());
        return LeadImportResponse.builder()
                .imported(imported)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    private Map<String, Integer> buildHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;
        for (Cell cell : headerRow) {
            String header = getCellStringValue(cell).toLowerCase().trim();
            if (!header.isBlank()) {
                map.put(header, cell.getColumnIndex());
            }
        }
        return map;
    }

    private Integer findColumn(Map<String, Integer> headerMap, String... candidates) {
        for (String candidate : candidates) {
            if (headerMap.containsKey(candidate.toLowerCase())) {
                return headerMap.get(candidate.toLowerCase());
            }
        }
        return null;
    }

    private String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        return getCellStringValue(cell);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private LeadSource mapPlatformToSource(String platform) {
        if (platform == null || platform.isBlank()) return LeadSource.META_ADS;
        String p = platform.toLowerCase();
        if (p.contains("meta") || p.contains("facebook") || p.contains("fb") || p.contains("instagram") || p.contains("ig")) return LeadSource.META_ADS;
        if (p.contains("google")) return LeadSource.GOOGLE_ADS;
        if (p.contains("organic") || p.contains("seo")) return LeadSource.ORGANIC;
        if (p.contains("referral") || p.contains("refer")) return LeadSource.REFERRAL;
        if (p.contains("walk")) return LeadSource.WALK_IN;
        return LeadSource.OTHER;
    }

    private String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
