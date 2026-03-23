package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.Gender;
import com.fmt.fmt_backend.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminVerifyAndCreateRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
        message = "Password must be at least 8 characters with 1 digit, 1 lowercase, 1 uppercase, 1 special character (@#$%^&+=!), and no spaces"
    )
    private String password;

    private String phoneNumber;

    private UserRole role; // defaults to STUDENT if null

    private Gender gender;

    private String city;
    private String state;
    private String country;
    private String postalCode;

    @NotBlank(message = "Email OTP is required")
    private String emailOtp;

    // Required only if phoneNumber was provided during send-otp step
    private String mobileOtp;
}
