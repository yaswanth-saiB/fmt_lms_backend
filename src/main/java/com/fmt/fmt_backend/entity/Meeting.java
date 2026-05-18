package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.MeetingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
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

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "meeting_batches",
        joinColumns = @JoinColumn(name = "meeting_id"),
        inverseJoinColumns = @JoinColumn(name = "batch_id")
    )
    private Set<Batch> batches = new HashSet<>();

    /** Mentor conducting this class — any mentor can take any batch's class */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

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
