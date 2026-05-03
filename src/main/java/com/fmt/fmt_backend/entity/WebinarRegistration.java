package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webinar_registrations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"webinar_id", "phone"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebinarRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webinar_id", nullable = false)
    private Webinar webinar;

    // Linked lead — created automatically on registration if phone is new
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @CreationTimestamp
    @Column(name = "registered_at", updatable = false)
    private LocalDateTime registeredAt;

    @Builder.Default
    @Column(name = "attended")
    private Boolean attended = false;

    // UTM tracking — which social post / campaign drove the registration
    @Column(name = "utm_source", length = 100)
    private String utmSource;

    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;
}
