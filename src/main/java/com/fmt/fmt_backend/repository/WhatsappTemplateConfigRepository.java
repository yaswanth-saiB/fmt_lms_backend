package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.WhatsappTemplateConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsappTemplateConfigRepository extends JpaRepository<WhatsappTemplateConfig, UUID> {

    Optional<WhatsappTemplateConfig> findByTemplateName(String templateName);

    Optional<WhatsappTemplateConfig> findByTemplateNameAndIsActiveTrue(String templateName);

    List<WhatsappTemplateConfig> findAllByOrderByTemplateNameAsc();

    boolean existsByTemplateName(String templateName);
}
