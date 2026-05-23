package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.entity.WhatsappConversation;
import com.fmt.fmt_backend.enums.ConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WhatsappConversationRepository extends JpaRepository<WhatsappConversation, UUID> {

    Optional<WhatsappConversation> findByPhone(String phone);

    @Query(value = """
        SELECT c FROM WhatsappConversation c
        LEFT JOIN c.lead l
        LEFT JOIN c.assignedTo a
        WHERE (:status IS NULL OR c.status = :status)
          AND (:assignedToId IS NULL OR a.id = :assignedToId)
          AND (:searchPattern IS NULL OR LOWER(l.name) LIKE :searchPattern
               OR LOWER(c.phone) LIKE :searchPattern)
          AND (:labelPattern IS NULL OR c.labels LIKE :labelPattern)
        ORDER BY c.lastMessageAt DESC NULLS LAST
        """,
        countQuery = """
        SELECT COUNT(c) FROM WhatsappConversation c
        LEFT JOIN c.lead l
        LEFT JOIN c.assignedTo a
        WHERE (:status IS NULL OR c.status = :status)
          AND (:assignedToId IS NULL OR a.id = :assignedToId)
          AND (:searchPattern IS NULL OR LOWER(l.name) LIKE :searchPattern
               OR LOWER(c.phone) LIKE :searchPattern)
          AND (:labelPattern IS NULL OR c.labels LIKE :labelPattern)
        """)
    Page<WhatsappConversation> findWithFilters(
            @Param("status") ConversationStatus status,
            @Param("assignedToId") UUID assignedToId,
            @Param("searchPattern") String searchPattern,
            @Param("labelPattern") String labelPattern,
            Pageable pageable);

    @Query("SELECT COUNT(c) FROM WhatsappConversation c WHERE c.unreadCount > 0")
    long countWithUnread();

    @Query("SELECT COUNT(c) FROM WhatsappConversation c WHERE c.status = com.fmt.fmt_backend.enums.ConversationStatus.NEEDS_HUMAN")
    long countNeedsHuman();

    @Query("SELECT SUM(c.unreadCount) FROM WhatsappConversation c WHERE c.assignedTo.id = :userId")
    Long sumUnreadForUser(@Param("userId") UUID userId);

    @Query("SELECT COUNT(c) FROM WhatsappConversation c WHERE c.assignedTo = :user AND c.status <> com.fmt.fmt_backend.enums.ConversationStatus.CLOSED")
    long countActiveByAssignedTo(@Param("user") User user);
}
