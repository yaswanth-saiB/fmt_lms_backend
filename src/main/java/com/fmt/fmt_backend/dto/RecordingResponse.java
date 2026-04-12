package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.RecordingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RecordingResponse {
    private UUID id;
    private String title;
    private UUID batchId;
    private String batchName;
    private String courseName;
    private RecordingStatus status;
    private Integer durationMins;
    private LocalDate recordedDate;
    private LocalDateTime createdAt;
    // Signed play URL returned only from /play endpoint — never stored here
}
