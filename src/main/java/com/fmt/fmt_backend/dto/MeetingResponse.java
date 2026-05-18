package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.MeetingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MeetingResponse {
    private UUID id;
    private String zoomMeetingId;
    private String topic;
    private List<BatchSummaryResponse> batches;
    private UUID mentorId;
    private String mentorName;
    private MeetingStatus status;
    private LocalDateTime scheduledAt;
    private Integer durationMins;
    private LocalDateTime createdAt;

    // Only included for MENTOR/ADMIN — never send to students
    private String startUrl;

    // Included for enrolled students and mentor
    private String joinUrl;
}
