package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.ConversationNote;
import com.fmt.fmt_backend.entity.WhatsappConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationNoteRepository extends JpaRepository<ConversationNote, UUID> {
    List<ConversationNote> findByConversationOrderByCreatedAtDesc(WhatsappConversation conversation);

    void deleteByConversation(WhatsappConversation conversation);
}
