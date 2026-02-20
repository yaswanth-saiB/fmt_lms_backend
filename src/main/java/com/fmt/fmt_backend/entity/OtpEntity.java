package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "otps")
@Data
public class OtpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String identifier; // email or phone number

    @Column(nullable = false)
    private String otpCode;

    @Enumerated(EnumType.STRING)
    private OtpType type; // EMAIL_VERIFICATION, MOBILE_VERIFICATION, LOGIN

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    private boolean verified = false;

    private LocalDateTime verifiedAt;

    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    public enum OtpType {
        EMAIL_VERIFICATION,
        MOBILE_VERIFICATION,
        LOGIN
    }
}