package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.entity.WhatsappCampaign;
import com.fmt.fmt_backend.entity.WhatsappCampaignRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsappCampaignRecipientRepository extends JpaRepository<WhatsappCampaignRecipient, UUID> {

    List<WhatsappCampaignRecipient> findByCampaignOrderBySentAtDesc(WhatsappCampaign campaign);

    void deleteByLead(Lead lead);

    Optional<WhatsappCampaignRecipient> findByWaMessageId(String waMessageId);

    // Checks both 10-digit and 12-digit (with 91 prefix) phone forms
    @Query("SELECT r FROM WhatsappCampaignRecipient r WHERE r.phone IN :phones AND r.failed = false AND r.sentAt >= :since ORDER BY r.sentAt DESC")
    List<WhatsappCampaignRecipient> findRecentByPhones(@Param("phones") List<String> phones, @Param("since") LocalDateTime since);
}
