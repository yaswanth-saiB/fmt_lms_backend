package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.WaMessageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_campaign_recipients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappCampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private WhatsappCampaign campaign;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "lead_name", length = 100)
    private String leadName;

    // Meta's message ID — null if send failed
    @Column(name = "wa_message_id", length = 100)
    private String waMessageId;

    @Column(name = "failed")
    @Builder.Default
    private Boolean failed = false;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // Delivery tracking — updated via webhook status events
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20)
    @Builder.Default
    private WaMessageStatus deliveryStatus = WaMessageStatus.SENT;

    // Reply tracking — true when lead sends any inbound message after campaign
    @Column(name = "replied")
    @Builder.Default
    private Boolean replied = false;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;
}
