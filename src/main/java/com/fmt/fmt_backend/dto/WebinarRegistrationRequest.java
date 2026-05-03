package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WebinarRegistrationRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String email;

    // UTM params for tracking which social post/campaign drove this registration
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
}
