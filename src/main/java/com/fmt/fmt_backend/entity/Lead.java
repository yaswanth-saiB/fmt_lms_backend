package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.LeadSource;
import com.fmt.fmt_backend.enums.LeadStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Lead extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", nullable = false, length = 20, unique = true)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "course_interest", length = 100)
    private String courseInterest;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 50)
    @Builder.Default
    private LeadSource source = LeadSource.META_ADS;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    @Builder.Default
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "dnp_count")
    @Builder.Default
    private Integer dnpCount = 0;

    @Column(name = "whatsapp_eligible")
    @Builder.Default
    private Boolean whatsappEligible = false;

    @Column(name = "whatsapp_sent")
    @Builder.Default
    private Boolean whatsappSent = false;

    @Column(name = "followup_datetime")
    private LocalDateTime followupDatetime;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_call_at")
    private LocalDateTime lastCallAt;

    // Fields from digital marketing Excel
    @Column(name = "current_level", length = 100)
    private String currentLevel;

    @Column(name = "preferred_learning_mode", length = 50)
    private String preferredLearningMode;
}
