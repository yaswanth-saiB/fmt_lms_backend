package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.Gender;
import com.fmt.fmt_backend.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    // PostgreSQL ENUM mapping
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "user_role_enum")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private UserRole userRole;

    @Column(name = "phone_number")
    private String phoneNumber;

    // PostgreSQL ENUM mapping
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "gender_enum")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Gender gender;

    private String city;
    private String state;
    private String country;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_email_verified")
    private Boolean isEmailVerified;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "is_mobile_verified")
    private Boolean isMobileVerified;

    @Column(name = "mobile_verified_at")
    private LocalDateTime mobileVerifiedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    @Column(name = "last_password_change_at")
    private LocalDateTime lastPasswordChangeAt;

    @PrePersist
    public void prePersist() {
        if (userRole == null) {
            userRole = UserRole.STUDENT;
        }
        if (isActive == null) {
            isActive = false;
        }
        if (isEmailVerified == null) {
            isEmailVerified = false;
        }
        if (isMobileVerified == null) {
            isMobileVerified = false;
        }
        if (failedLoginAttempts == null) {
            failedLoginAttempts = 0;
        }
        if (lastPasswordChangeAt == null) {
            lastPasswordChangeAt = LocalDateTime.now();
        }
    }
}

// IMPORTANT FIELDS EXPLAINED:
// 1. UUID id: Better than auto-increment (security, no sequence guessing)
// 2. @Enumerated: Stores enum as string (MALE, FEMALE) not numbers
// 3. Boolean fields: Track status (true/false)
// 4. LocalDateTime fields: Track when things happened
// 5. @PrePersist: Sets defaults automatically