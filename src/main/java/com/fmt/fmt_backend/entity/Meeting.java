package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.MeetingStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meetings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Meeting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(name = "zoom_meeting_id")
    private String zoomMeetingId;

    @Column(nullable = false)
    private String topic;

    // SECURITY: Never expose start_url to students
    @Column(name = "start_url", columnDefinition = "TEXT")
    private String startUrl;

    @Column(name = "join_url", columnDefinition = "TEXT")
    private String joinUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeetingStatus status;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // Duration in minutes
    @Column(name = "duration_mins")
    private Integer durationMins;

    @PrePersist
    public void prePersist() {
        if (status == null) status = MeetingStatus.UPCOMING;
        if (durationMins == null) durationMins = 120;
    }
}
