package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.MessageDirection;
import com.fmt.fmt_backend.enums.WaMessageStatus;
import com.fmt.fmt_backend.enums.WaMessageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WhatsappMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private WhatsappConversation conversation;

    // Meta's message ID — used to match status updates
    @Column(name = "whatsapp_message_id", length = 100)
    private String whatsappMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20, nullable = false)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 30)
    @Builder.Default
    private WaMessageType messageType = WaMessageType.TEXT;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "button_id", length = 100)
    private String buttonId;

    @Column(name = "button_title", length = 200)
    private String buttonTitle;

    @Column(name = "template_name", length = 100)
    private String templateName;

    // WhatsApp media ID — set for image/document/audio/video inbound messages
    @Column(name = "media_id", length = 500)
    private String mediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private WaMessageStatus status = WaMessageStatus.SENT;

    @Column(name = "is_bot_message")
    @Builder.Default
    private Boolean isBotMessage = false;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by")
    private User sentBy;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
