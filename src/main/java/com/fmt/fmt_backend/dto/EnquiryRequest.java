package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.entity.Enquiry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EnquiryRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Please enter a valid 10-digit Indian mobile number")
    private String mobile;

    private String city;

    private Enquiry.ExperienceLevel experienceLevel;

    private String areaOfInterest;

    private String message;
}