package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private DeviceEntity device;

    /**
     * The session this refresh token belongs to.
     * When a new access token is issued on refresh, the same sessionId is
     * embedded in it — keeping the session identity continuous.
     */
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private boolean revoked = false;  // NEW: Track if manually revoked

    private LocalDateTime revokedAt;  // NEW: When it was revoked

    private String revokedReason;     // NEW: Why it was revoked (logout, security, etc.)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isValid() {
        return !revoked && LocalDateTime.now().isBefore(expiresAt);
    }
}