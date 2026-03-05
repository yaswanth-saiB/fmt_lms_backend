package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.entity.Batch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDTO {
    private UUID id;
    private String name;
    private UUID courseId;
    private String courseName;
    private UUID mentorId;
    private String mentorName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxStudents;
    private Integer currentStudents;
    private String status;
    //private Integer meetingCount;
    private LocalDateTime createdAt;

    public static BatchDTO fromEntity(Batch batch) {
        return BatchDTO.builder()
                .id(batch.getId())
                .name(batch.getName())
                .courseId(batch.getCourse().getId())
                .courseName(batch.getCourse().getTitle())
                .mentorId(batch.getMentor().getId())
                .mentorName(batch.getMentor().getFirstName() + " " + batch.getMentor().getLastName())
                .startDate(batch.getStartDate())
                .endDate(batch.getEndDate())
                .maxStudents(batch.getMaxStudents())
                .currentStudents(batch.getCurrentStudents())
                .status(batch.getStatus().name())
                //.meetingCount(batch.getMeetings() != null ? batch.getMeetings().size() : 0)
                .createdAt(batch.getCreatedAt())
                .build();
    }
}
