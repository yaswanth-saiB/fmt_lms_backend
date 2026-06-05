package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Per-template config that Meta's API does NOT give us:
 *   - The media ID (uploaded via POST /{phone-id}/media) for IMAGE/VIDEO/DOC headers.
 *   - The action to run when each button is pressed.
 *
 * Everything else about a template — header type, body text, button labels,
 * variables — is fetched live from Meta via WhatsAppApiService.getApprovedTemplates()
 * (cached). This entity is intentionally slim.
 */
@Entity
@Table(
        name = "whatsapp_template_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = "template_name"),
        indexes = @Index(name = "idx_wa_template_config_name", columnList = "template_name")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WhatsappTemplateConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Matches the Meta template name exactly — e.g. "fmt_june_batch_campaign_hit". */
    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    /** Optional admin-visible note describing what this template is for. */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Stable WhatsApp media ID from POST /{phone-id}/media — used at send time.
     * Null when the template's Meta header is TEXT or none.
     */
    @Column(name = "header_media_id", length = 64)
    private String headerMediaId;

    /**
     * IMAGE | VIDEO | DOCUMENT — drives which JSON key we use in the Meta send payload
     * (`image.id` vs `video.id` vs `document.id`). Must match the template's actual
     * header format in Meta. Null when there's no media header.
     */
    @Column(name = "header_media_type", length = 16)
    private String headerMediaType;

    /** Friendly label for the media (e.g. "June Batch Banner"). Admin-visible only. */
    @Column(name = "header_label", length = 200)
    private String headerLabel;

    /**
     * JSON array of button → action mappings. Shape:
     *   [
     *     { "payload": "RESERVE MY SEAT",
     *       "actionType": "RESERVE_SEAT",
     *       "actionParams": { "batchName": "HIT June Batch" },
     *       "description": "Hot lead from campaign" },
     *     ...
     *   ]
     * Null/empty means the chatbot falls back to legacy hardcoded handling.
     */
    @Lob
    @Column(name = "buttons_json", columnDefinition = "TEXT")
    private String buttonsJson;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
}
