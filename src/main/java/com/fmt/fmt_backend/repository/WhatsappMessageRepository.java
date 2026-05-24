package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.WhatsappConversation;
import com.fmt.fmt_backend.entity.WhatsappMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsappMessageRepository extends JpaRepository<WhatsappMessage, UUID> {

    List<WhatsappMessage> findByConversationOrderBySentAtAsc(WhatsappConversation conversation);

    Optional<WhatsappMessage> findByWhatsappMessageId(String whatsappMessageId);

    void deleteByConversation(WhatsappConversation conversation);

    @org.springframework.data.jpa.repository.Query("""
        SELECT COUNT(m) > 0 FROM WhatsappMessage m
        WHERE m.conversation = :conv
          AND m.isBotMessage = true
          AND m.direction = com.fmt.fmt_backend.enums.MessageDirection.OUTBOUND
          AND m.sentAt > :since
        """)
    boolean existsRecentBotMessage(
            @org.springframework.data.repository.query.Param("conv") WhatsappConversation conv,
            @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
