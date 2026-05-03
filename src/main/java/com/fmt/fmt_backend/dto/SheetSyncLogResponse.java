package com.fmt.fmt_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmt.fmt_backend.entity.SheetSyncLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SheetSyncLogResponse {

    private UUID id;
    private LocalDateTime syncedAt;
    private int sheetRowsRead;
    private int importedCount;
    private int skippedCount;
    private String triggerType;
    private String triggeredByName;
    private List<String> errors;

    public static SheetSyncLogResponse from(SheetSyncLog log) {
        String triggeredByName = null;
        if (log.getTriggeredBy() != null) {
            triggeredByName = log.getTriggeredBy().getFirstName() + " " + log.getTriggeredBy().getLastName();
        }

        List<String> errorList = Collections.emptyList();
        if (log.getErrors() != null && !log.getErrors().isBlank()) {
            errorList = Arrays.asList(log.getErrors().split("\n"));
        }

        return SheetSyncLogResponse.builder()
                .id(log.getId())
                .syncedAt(log.getSyncedAt())
                .sheetRowsRead(log.getSheetRowsRead())
                .importedCount(log.getImportedCount())
                .skippedCount(log.getSkippedCount())
                .triggerType(log.getTriggerType())
                .triggeredByName(triggeredByName)
                .errors(errorList.isEmpty() ? null : errorList)
                .build();
    }
}
