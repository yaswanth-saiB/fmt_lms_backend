package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.Gender;
import com.fmt.fmt_backend.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private UserRole role;
    private Gender gender;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private Boolean isActive;
    private Boolean isEmailVerified;
    private Boolean isMobileVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private Integer failedLoginAttempts;
    private Boolean mustChangePassword;
}
