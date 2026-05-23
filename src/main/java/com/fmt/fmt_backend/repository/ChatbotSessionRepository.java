package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.ChatbotSession;
import com.fmt.fmt_backend.entity.WhatsappConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatbotSessionRepository extends JpaRepository<ChatbotSession, UUID> {

    Optional<ChatbotSession> findByConversation(WhatsappConversation conversation);
}
