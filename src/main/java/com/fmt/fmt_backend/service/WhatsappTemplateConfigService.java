package com.fmt.fmt_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmt.fmt_backend.dto.TemplateButtonDto;
import com.fmt.fmt_backend.dto.WhatsappTemplateConfigDto;
import com.fmt.fmt_backend.dto.WhatsappTemplateConfigRequest;
import com.fmt.fmt_backend.entity.WhatsappTemplateConfig;
import com.fmt.fmt_backend.repository.WhatsappTemplateConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappTemplateConfigService {

    private final WhatsappTemplateConfigRepository repository;
    private final ObjectMapper objectMapper;

    // ─── CRUD ─────────────────────────────────────────────────────────────────────

    public List<WhatsappTemplateConfigDto> getAll() {
        return repository.findAllByOrderByTemplateNameAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public WhatsappTemplateConfigDto getById(UUID id) {
        return toDto(repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template config not found")));
    }

    @Transactional
    public WhatsappTemplateConfigDto create(WhatsappTemplateConfigRequest req, UUID createdByUserId) {
        if (repository.existsByTemplateName(req.getTemplateName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Template config already exists for '" + req.getTemplateName() + "'");
        }
        WhatsappTemplateConfig entity = WhatsappTemplateConfig.builder()
                .templateName(req.getTemplateName())
                .description(req.getDescription())
                .headerMediaId(emptyToNull(req.getHeaderMediaId()))
                .headerMediaType(normalizeMediaType(req.getHeaderMediaType()))
                .headerLabel(emptyToNull(req.getHeaderLabel()))
                .buttonsJson(serializeButtons(req.getButtons()))
                .isActive(req.getIsActive() == null ? true : req.getIsActive())
                .createdByUserId(createdByUserId)
                .build();
        return toDto(repository.save(entity));
    }

    @Transactional
    public WhatsappTemplateConfigDto update(UUID id, WhatsappTemplateConfigRequest req) {
        WhatsappTemplateConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template config not found"));

        // Allow renaming, but block if collides with another row's template_name
        if (!entity.getTemplateName().equals(req.getTemplateName())
                && repository.existsByTemplateName(req.getTemplateName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another config already uses template name '" + req.getTemplateName() + "'");
        }

        entity.setTemplateName(req.getTemplateName());
        entity.setDescription(req.getDescription());
        entity.setHeaderMediaId(emptyToNull(req.getHeaderMediaId()));
        entity.setHeaderMediaType(normalizeMediaType(req.getHeaderMediaType()));
        entity.setHeaderLabel(emptyToNull(req.getHeaderLabel()));
        entity.setButtonsJson(serializeButtons(req.getButtons()));
        if (req.getIsActive() != null) entity.setIsActive(req.getIsActive());
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template config not found");
        }
        repository.deleteById(id);
    }

    @Transactional
    public WhatsappTemplateConfigDto toggleActive(UUID id) {
        WhatsappTemplateConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template config not found"));
        entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
        return toDto(repository.save(entity));
    }

    // ─── Internal lookups (used by WhatsAppApiService + ChatbotEngine) ───────────

    /** Returns the configured media id for a template, or empty if no active config exists. */
    public Optional<String> findHeaderMediaIdForTemplate(String templateName) {
        return repository.findByTemplateNameAndIsActiveTrue(templateName)
                .map(WhatsappTemplateConfig::getHeaderMediaId);
    }

    /**
     * Returns (mediaId, mediaType) for a template, or empty if no active config
     * exists or the config has no media. Type is upper-cased (IMAGE / VIDEO / DOCUMENT).
     */
    public Optional<HeaderResolution> findHeaderForTemplate(String templateName) {
        return repository.findByTemplateNameAndIsActiveTrue(templateName)
                .filter(c -> c.getHeaderMediaId() != null && !c.getHeaderMediaId().isBlank())
                .map(c -> new HeaderResolution(c.getHeaderMediaId(), normalizeMediaType(c.getHeaderMediaType())));
    }

    /** Small carrier for the (mediaId, mediaType) pair returned by findHeaderForTemplate. */
    public record HeaderResolution(String mediaId, String mediaType) {}

    /**
     * Looks up a button action by payload across ALL active template configs.
     * Returns the first match (good enough — button text is typically unique across templates).
     * Returns empty if no matching button is configured.
     */
    public Optional<TemplateButtonDto> findActionForButtonPayload(String payload) {
        if (payload == null || payload.isBlank()) return Optional.empty();
        String normalized = payload.trim();
        // Case-insensitive match — WhatsApp sometimes returns mixed case
        for (WhatsappTemplateConfig cfg : repository.findAllByOrderByTemplateNameAsc()) {
            if (!Boolean.TRUE.equals(cfg.getIsActive())) continue;
            List<TemplateButtonDto> buttons = deserializeButtons(cfg.getButtonsJson());
            for (TemplateButtonDto b : buttons) {
                if (b.getPayload() != null && b.getPayload().trim().equalsIgnoreCase(normalized)) {
                    return Optional.of(b);
                }
            }
        }
        return Optional.empty();
    }

    // ─── Mapping helpers ──────────────────────────────────────────────────────────

    private WhatsappTemplateConfigDto toDto(WhatsappTemplateConfig e) {
        return WhatsappTemplateConfigDto.builder()
                .id(e.getId())
                .templateName(e.getTemplateName())
                .description(e.getDescription())
                .headerMediaId(e.getHeaderMediaId())
                .headerMediaType(e.getHeaderMediaType())
                .headerLabel(e.getHeaderLabel())
                .buttons(deserializeButtons(e.getButtonsJson()))
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private static String normalizeMediaType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "IMAGE", "VIDEO", "DOCUMENT" -> upper;
            default -> null;
        };
    }

    private String serializeButtons(List<TemplateButtonDto> buttons) {
        if (buttons == null || buttons.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(buttons);
        } catch (Exception e) {
            log.warn("Failed to serialize template buttons: {}", e.getMessage());
            return null;
        }
    }

    private List<TemplateButtonDto> deserializeButtons(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize template buttons JSON, returning empty: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
