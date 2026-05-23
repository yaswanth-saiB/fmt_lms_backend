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
}
