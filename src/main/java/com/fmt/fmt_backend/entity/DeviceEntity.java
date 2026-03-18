package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String deviceFingerprint;

    private String ipAddress;

    private String userAgent;

    private String deviceName; // "Chrome on Windows", etc.

    private LocalDateTime lastActiveAt;

    private LocalDateTime firstSeenAt;

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private boolean isStreaming = false; // For video streaming

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}