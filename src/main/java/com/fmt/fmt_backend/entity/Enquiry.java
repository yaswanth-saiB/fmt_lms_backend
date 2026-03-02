package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "enquiries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = false)  // Not unique - same person can enquire multiple times
    private String mobile;

    private String city;

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    private String areaOfInterest;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    private EnquiryStatus status;

    private String ipAddress;

    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ExperienceLevel {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    public enum EnquiryStatus {
        NEW, CONTACTED, CLOSED
    }
}