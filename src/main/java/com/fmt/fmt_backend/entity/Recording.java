package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.RecordingStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recordings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Recording extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    // SECURITY: Never expose to frontend — backend use only
    @Column(name = "zoom_download_url", columnDefinition = "TEXT")
    private String zoomDownloadUrl;

    // SECURITY: Never expose directly — always generate signed URL
    @Column(name = "bunny_video_id")
    private String bunnyVideoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordingStatus status;

    @Column(name = "duration_mins")
    private Integer durationMins;

    @Column(name = "title")
    private String title;

    // When this recording expires and is deleted from Cloudflare
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (status == null) status = RecordingStatus.PROCESSING;
    }
}
