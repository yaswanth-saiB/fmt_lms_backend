package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.entity.WhatsappTemplateConfig;
import com.fmt.fmt_backend.repository.WhatsappTemplateConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * On first startup, pre-creates WhatsappTemplateConfig rows for templates we already
 * know about — populated from the existing env-var media ids. Idempotent: if a row
 * already exists for a template name, we skip it.
 *
 * After this seeder runs, the env vars (WHATSAPP_IMG_*) become effectively unused by
 * the DB-driven lookup path. The legacy hardcoded switch in WhatsAppApiService is kept
 * as a defensive fallback only.
 *
 * Buttons are NOT seeded — old chatbot continues handling them via the legacy
 * normalizeMenuTrigger switch. NEW templates configured via admin UI get explicit
 * button configs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsappTemplateConfigSeeder implements ApplicationRunner {

    private final WhatsappTemplateConfigRepository repository;

    @Value("${whatsapp.media.img-welcome}")           private String imgWelcome;
    @Value("${whatsapp.media.img-hit:}")              private String imgHit;
    @Value("${whatsapp.media.img-forex:}")            private String imgForex;
    @Value("${whatsapp.media.img-options:}")          private String imgOptions;
    @Value("${whatsapp.media.img-june-batch:}")       private String imgJuneBatch;

    @Override
    public void run(ApplicationArguments args) {
        // List of (template_name, media_id, label, description) seed rows.
        // Use IMAGE for all — these are all image-header templates.
        record Seed(String name, String mediaId, String label, String description) {}
        List<Seed> seeds = List.of(
                new Seed("fmt_click_wa_welcome",      imgWelcome,    "Welcome Banner",        "Sent to organic inbound leads as the chatbot's intro template."),
                new Seed("fmt_demo_booking_confirm",  imgWelcome,    "Welcome Banner",        "Date-selection template sent when user picks 'Book Free Demo'."),
                new Seed("fmt_hit_program_details",   imgHit,        "HIT Program Banner",    "Sent when user picks 'HIT' from the course menu."),
                new Seed("fmt_forex_program_details", imgForex,      "Forex Program Banner",  "Sent when user picks 'Forex' from the course menu."),
                new Seed("fmt_options_program_details", imgOptions,  "Options Program Banner","Optional — sent when user picks 'Options' from course menu (if template exists)."),
                new Seed("fmt_june_batch_campaign_hit", imgJuneBatch,"June Batch HIT Campaign","HIT June batch promo campaign template.")
        );

        int created = 0;
        int skipped = 0;
        for (Seed s : seeds) {
            if (s.mediaId() == null || s.mediaId().isBlank()) {
                // No media id configured for this template — skip seeding it
                continue;
            }
            if (repository.existsByTemplateName(s.name())) {
                skipped++;
                continue;
            }
            WhatsappTemplateConfig cfg = WhatsappTemplateConfig.builder()
                    .templateName(s.name())
                    .description(s.description())
                    .headerMediaId(s.mediaId())
                    .headerMediaType("IMAGE")
                    .headerLabel(s.label())
                    .isActive(true)
                    .build();
            repository.save(cfg);
            created++;
        }
        log.info("WhatsappTemplateConfigSeeder: created={} skipped={} (already existed)", created, skipped);
    }
}
