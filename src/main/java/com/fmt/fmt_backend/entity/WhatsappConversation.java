package com.fmt.fmt_backend.entity;

import com.fmt.fmt_backend.enums.ChatbotState;
import com.fmt.fmt_backend.enums.ConversationEntryPoint;
import com.fmt.fmt_backend.enums.ConversationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WhatsappConversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.OPEN;

    @Column(name = "chatbot_active")
    @Builder.Default
    private Boolean chatbotActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "chatbot_state", length = 50)
    @Builder.Default
    private ChatbotState chatbotState = ChatbotState.INITIAL;

    @Column(name = "window_expires_at")
    private LocalDateTime windowExpiresAt;

    @Column(name = "last_message", columnDefinition = "TEXT")
    private String lastMessage;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "unread_count")
    @Builder.Default
    private Integer unreadCount = 0;

    // Comma-separated labels e.g. "Hot Lead,Payment Pending"
    @Column(name = "labels", length = 500)
    private String labels;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_point", length = 30)
    @Builder.Default
    private ConversationEntryPoint entryPoint = ConversationEntryPoint.INBOUND;
}
