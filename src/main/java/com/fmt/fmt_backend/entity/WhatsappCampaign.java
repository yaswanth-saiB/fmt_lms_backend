package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.CampaignStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WhatsappCampaign extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    // JSON array of param values: ["{{name}}"] — {{name}} replaced with lead name at send time
    @Column(name = "template_params", columnDefinition = "TEXT")
    private String templateParams;

    // JSON array of param variable names matching templateParams: ["customer_name"] or [null] for positional
    @Column(name = "template_param_names", columnDefinition = "TEXT")
    private String templateParamNames;

    // Comma-separated LeadStatus values; null = no status filter (all leads)
    @Column(name = "lead_statuses", length = 500)
    private String leadStatuses;

    // Null = all course interests
    @Column(name = "course_interest", length = 100)
    private String courseInterest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "total_count")
    @Builder.Default
    private Integer totalCount = 0;

    @Column(name = "success_count")
    @Builder.Default
    private Integer successCount = 0;

    @Column(name = "fail_count")
    @Builder.Default
    private Integer failCount = 0;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
