package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.WhatsappCampaign;
import com.fmt.fmt_backend.entity.WhatsappCampaignRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WhatsappCampaignRecipientRepository extends JpaRepository<WhatsappCampaignRecipient, UUID> {
    List<WhatsappCampaignRecipient> findByCampaignOrderBySentAtDesc(WhatsappCampaign campaign);

    void deleteByLead(com.fmt.fmt_backend.entity.Lead lead);
}
