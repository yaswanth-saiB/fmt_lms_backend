package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EnquiryResponse {
    private UUID id;
    private String name;
    private String mobile;
    private String city;
    private String experienceLevel;
    private String areaOfInterest;
    private String message;
    private String status;
    private LocalDateTime createdAt;
}
