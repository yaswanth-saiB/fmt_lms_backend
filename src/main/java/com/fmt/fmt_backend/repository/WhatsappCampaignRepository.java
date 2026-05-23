package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.WhatsappCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WhatsappCampaignRepository extends JpaRepository<WhatsappCampaign, UUID> {
    List<WhatsappCampaign> findAllByOrderByCreatedAtDesc();
}
