package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.ClosingBlocker;
import com.fmt.fmt_backend.enums.DemoType;
import com.fmt.fmt_backend.enums.LeadSource;
import com.fmt.fmt_backend.enums.LeadStatus;
import com.fmt.fmt_backend.enums.PreferredTiming;
import java.math.BigDecimal;
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

    // Fields from digital marketing Excel / Google Sheet
    @Column(name = "current_level", length = 100)
    private String currentLevel;

    @Column(name = "preferred_learning_mode", length = 50)
    private String preferredLearningMode;

    // Original timestamp from the Google Sheet (created_time column)
    @Column(name = "sheet_created_at")
    private LocalDateTime sheetCreatedAt;

    // Demo tracking
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demo_mentor_id")
    private User demoMentor;

    @Column(name = "demo_scheduled_at")
    private LocalDateTime demoScheduledAt;

    @Column(name = "demo_conducted_at")
    private LocalDateTime demoConductedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "demo_type", length = 20)
    private DemoType demoType;

    // Closing stage
    @Enumerated(EnumType.STRING)
    @Column(name = "closing_blocker", length = 50)
    private ClosingBlocker closingBlocker;

    @Column(name = "closing_comment", columnDefinition = "TEXT")
    private String closingComment;

    // Preferred call timing (from Google Sheet / webinar form)
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_timings", length = 20)
    private PreferredTiming preferredTimings;

    // Alternate phone given via WhatsApp response
    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;

    // Who closed the deal (set when status → PAYMENT_DONE)
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    // Agreed course fee at closing stage
    @Column(name = "course_fee", precision = 10, scale = 2)
    private BigDecimal courseFee;

    // Demo booking captured via chatbot flow
    @Column(name = "chatbot_demo_date", columnDefinition = "TEXT")
    private String chatbotDemoDate;

    @Column(name = "chatbot_demo_mode", length = 20)
    private String chatbotDemoMode;

    @Column(name = "chatbot_demo_time", length = 20)
    private String chatbotDemoTime;

    @Column(name = "chatbot_demo_booked_at")
    private LocalDateTime chatbotDemoBookedAt;
}
