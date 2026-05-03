-- ============================================================
-- Lead CRM Migration v3
-- Run this on the EC2 PostgreSQL instance (fmt database)
-- Safe to run multiple times — uses IF NOT EXISTS / IF EXISTS
-- ============================================================

-- ─── 1. ALTER leads table — add new columns ─────────────────

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS preferred_timings VARCHAR(20),
    ADD COLUMN IF NOT EXISTS alternate_phone   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS closed_by         UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS course_fee        NUMERIC(10, 2);

-- ─── 2. lead_payments ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS lead_payments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id      UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
    amount       NUMERIC(10, 2) NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    due_date     DATE,
    paid_at      TIMESTAMP,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes        TEXT,
    recorded_by  UUID REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lead_payments_lead_id ON lead_payments(lead_id);

-- ─── 3. webinars ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS webinars (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    scheduled_at       TIMESTAMP NOT NULL,
    zoom_link          VARCHAR(500),
    host_mentor_id     UUID REFERENCES users(id),
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    max_capacity       INTEGER,
    registration_count INTEGER NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── 4. webinar_registrations ────────────────────────────────

CREATE TABLE IF NOT EXISTS webinar_registrations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webinar_id   UUID NOT NULL REFERENCES webinars(id) ON DELETE CASCADE,
    lead_id      UUID REFERENCES leads(id),
    name         VARCHAR(100),
    phone        VARCHAR(20) NOT NULL,
    email        VARCHAR(100),
    registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    attended     BOOLEAN NOT NULL DEFAULT FALSE,
    utm_source   VARCHAR(100),
    utm_medium   VARCHAR(100),
    utm_campaign VARCHAR(100),
    CONSTRAINT uq_webinar_phone UNIQUE (webinar_id, phone)
);

CREATE INDEX IF NOT EXISTS idx_webinar_registrations_webinar_id ON webinar_registrations(webinar_id);
CREATE INDEX IF NOT EXISTS idx_webinar_registrations_lead_id   ON webinar_registrations(lead_id);

-- ─── 5. sheet_sync_logs — ensure exists (in case running fresh) ──

CREATE TABLE IF NOT EXISTS sheet_sync_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sheet_rows_read INTEGER,
    imported_count  INTEGER,
    skipped_count   INTEGER,
    trigger_type    VARCHAR(20),
    triggered_by    UUID REFERENCES users(id),
    errors          TEXT,
    synced_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── Done ──────────────────────────────────────────────────────
-- After running this, restart the Spring Boot service:
--   sudo systemctl restart fmt-backend
