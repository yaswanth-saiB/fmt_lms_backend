package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents one login session for a user.
 * The sessionId is embedded in the JWT access token so every request
 * carries its session identity without a DB lookup.
 *
 * Sessions are used for:
 *  - Audit log (which device, what IP)
 *  - "Log out from all devices" (revoke all sessions)
 *  - Future: per-session revocation before access token expires
 */
@Entity
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_user_sessions_device_id", columnList = "device_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @Column(name = "session_id")
    private UUID sessionId; // This UUID is embedded into the JWT — it IS the session identity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private DeviceEntity device;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean active = true;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "ip_address")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isValid() {
        return active && LocalDateTime.now().isBefore(expiresAt);
    }
}
