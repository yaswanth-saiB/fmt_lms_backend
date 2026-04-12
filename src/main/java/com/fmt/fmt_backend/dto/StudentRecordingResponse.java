package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.RecordingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Recording summary returned to students.
 * Does NOT include bunnyVideoId, zoomDownloadUrl, or playUrl.
 * Only AVAILABLE recordings are ever returned — status filter is applied server-side.
 */
@Data
@Builder
public class StudentRecordingResponse {
    private UUID id;
    private String title;
    private LocalDate recordedDate;
    private Integer durationMins;
    private RecordingStatus status;
}
