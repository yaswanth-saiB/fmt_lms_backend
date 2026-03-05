package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.entity.Enrollment;
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
public class EnrollmentDTO {
    private UUID id;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private UUID batchId;
    private String batchName;
    private String courseName;
    private LocalDateTime enrolledAt;
    private String status;
    private Integer progressPercentage;

    public static EnrollmentDTO fromEntity(Enrollment enrollment) {
        return EnrollmentDTO.builder()
                .id(enrollment.getId())
                .studentId(enrollment.getStudent().getId())
                .studentName(enrollment.getStudent().getFirstName() + " " + enrollment.getStudent().getLastName())
                .studentEmail(enrollment.getStudent().getEmail())
                .batchId(enrollment.getBatch().getId())
                .batchName(enrollment.getBatch().getName())
                .courseName(enrollment.getBatch().getCourse().getTitle())
                .enrolledAt(enrollment.getEnrolledAt())
                .status(enrollment.getStatus().name())
                .progressPercentage(enrollment.getProgressPercentage())
                .build();
    }
}