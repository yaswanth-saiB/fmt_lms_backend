package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.entity.Meeting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDTO {
    private UUID id;
    private String zoomMeetingId;
    private String topic;
    private String description;
    private UUID batchId;
    private String batchName;
    private UUID mentorId;
    private String mentorName;
    private LocalDateTime startTime;
    private Integer durationMinutes;
    private String joinUrl;      // For students
    private String startUrl;      // For mentors (sensitive)
    private String password;
    private String status;
    private String recordingUrl;
    private LocalDateTime createdAt;

    public static MeetingDTO fromEntity(Meeting meeting) {
        MeetingDTO dto = MeetingDTO.builder()
                .id(meeting.getId())
                .zoomMeetingId(meeting.getZoomMeetingId())
                .topic(meeting.getTopic())
                .description(meeting.getDescription())
                .batchId(meeting.getBatch().getId())
                .batchName(meeting.getBatch().getName())
                .mentorId(meeting.getMentor().getId())
                .mentorName(meeting.getMentor().getFirstName() + " " + meeting.getMentor().getLastName())
                .startTime(meeting.getStartTime())
                .durationMinutes(meeting.getDurationMinutes())
                .joinUrl(meeting.getJoinUrl())
                .startUrl(meeting.getStartUrl())
                .password(meeting.getPassword())
                .status(meeting.getStatus().name())
                .recordingUrl(meeting.getRecordingUrl())
                .createdAt(meeting.getCreatedAt())
                .build();
        return dto;
    }
}
