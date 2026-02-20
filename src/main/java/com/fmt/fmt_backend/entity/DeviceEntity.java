package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Data
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

    private boolean isActive = true;

    private boolean isStreaming = false; // For video streaming

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}