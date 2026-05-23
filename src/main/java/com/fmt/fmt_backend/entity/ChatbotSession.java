package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chatbot_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChatbotSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", unique = true, nullable = false)
    private WhatsappConversation conversation;

    @Column(name = "current_state", length = 50)
    private String currentState;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    // JSON blob for temporary context (e.g. pending demo date, name confirmation)
    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;
}
