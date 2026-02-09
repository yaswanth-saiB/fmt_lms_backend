package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmailVerificationToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)  // One token per user
    @JoinColumn(name = "user_id", nullable = false)
    private User user;  // Which user this token belongs to

    @Column(nullable = false, unique = true)
    private String token;  // Random unique string

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;  // Token validity (24 hours)
}

// WHY SEPARATE TABLE?
// 1. Security: Tokens not in user table
// 2. Expiry: Can set expiration time
// 3. History: Can track multiple tokens (if resend)
// 4. Clean: User table stays clean
