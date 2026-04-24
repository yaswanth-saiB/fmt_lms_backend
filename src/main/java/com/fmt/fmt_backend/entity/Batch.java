package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Batch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_students")
    private Integer maxStudents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "bunny_collection_id")
    private String bunnyCollectionId;

    @PrePersist
    public void prePersist() {
        if (status == null) status = BatchStatus.UPCOMING;
        if (maxStudents == null) maxStudents = 30;
    }
}
