package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.entity.Course;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDTO {
    private UUID id;
    private String title;
    private String description;
    private UUID mentorId;
    private String mentorName;
    private BigDecimal price;
    private Integer durationHours;
    private String status;
    private Integer batchCount;
    private LocalDateTime createdAt;

    // Static method to convert Entity to DTO
    public static CourseDTO fromEntity(Course course) {
        return CourseDTO.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .mentorId(course.getMentor().getId())
                .mentorName(course.getMentor().getFirstName() + " " + course.getMentor().getLastName())
                .price(course.getPrice())
                .durationHours(course.getDurationHours())
                .status(course.getStatus().name())
                .batchCount(course.getBatches() != null ? course.getBatches().size() : 0)
                .createdAt(course.getCreatedAt())
                .build();
    }
}