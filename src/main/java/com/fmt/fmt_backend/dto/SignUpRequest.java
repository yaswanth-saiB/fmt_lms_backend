package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SignUpRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
            message = "Password must contain: 1 digit, 1 lowercase, 1 uppercase, 1 special character, no spaces"
    )
    private String password;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
    private String phoneNumber;

    private Gender gender;  // Using enum!

    private String city;
    private String state;
    private String country;
    private String postalCode;
}

// VALIDATION ANNOTATIONS:
// @NotBlank = not null, not empty, not just spaces
// @Size = length constraints
// @Email = validates email format
// @Pattern = regex validation (phone, password)
// Spring validates automatically, returns 400 if invalid