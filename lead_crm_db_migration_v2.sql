-- =============================================================================
-- FMT Lead CRM — DB Migration v2
-- Run this ONLY if you already ran lead_crm_db_migration.sql (v1)
-- Safe to run on a running DB — all statements use IF NOT EXISTS / IF EXISTS
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. New columns on `leads` table
-- -----------------------------------------------------------------------------

-- Google Sheet original timestamp (used for lead-age calculations)
ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS sheet_created_at TIMESTAMP;

-- Demo tracking
ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS demo_mentor_id UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS demo_scheduled_at TIMESTAMP;

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS demo_conducted_at TIMESTAMP;

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS demo_type VARCHAR(20);   -- ONLINE | OFFLINE

-- Closing stage
ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS closing_blocker VARCHAR(50);

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS closing_comment TEXT;

-- Extra fields synced from Google Sheet (digital marketing Excel columns)
ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS current_level VARCHAR(100);

ALTER TABLE leads
    ADD COLUMN IF NOT EXISTS preferred_learning_mode VARCHAR(50);

-- -----------------------------------------------------------------------------
-- 2. New enum values for lead_status (VARCHAR — no ENUM type, no migration needed)
--    Values added in code: DEMO_NO_SHOW
--    (VARCHAR columns accept any string value — just documenting here)
-- -----------------------------------------------------------------------------

-- If you're using a CHECK constraint on status, add the new value:
-- ALTER TABLE leads DROP CONSTRAINT IF EXISTS leads_status_check;
-- (Hibernate ddl-auto: update does not add CHECK constraints, so this is a no-op
--  unless you added one manually.)

-- -----------------------------------------------------------------------------
-- 3. Indexes for new columns
-- -----------------------------------------------------------------------------

-- Lead age queries (aging/stale leads)
CREATE INDEX IF NOT EXISTS idx_leads_sheet_created_at
    ON leads (sheet_created_at);

-- Demo mentor lookup
CREATE INDEX IF NOT EXISTS idx_leads_demo_mentor_id
    ON leads (demo_mentor_id);

-- Demo scheduled date (useful for demo calendar views)
CREATE INDEX IF NOT EXISTS idx_leads_demo_scheduled_at
    ON leads (demo_scheduled_at);

-- Composite index for aging/stale stats query
-- COALESCE(sheet_created_at, created_at) is not indexable directly,
-- so we index both columns separately and let Postgres pick
CREATE INDEX IF NOT EXISTS idx_leads_created_at
    ON leads (created_at);

-- -----------------------------------------------------------------------------
-- 4. Create `sheet_sync_logs` table (new in v2)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sheet_sync_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    synced_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    sheet_rows_read INT       NOT NULL DEFAULT 0,
    imported_count  INT       NOT NULL DEFAULT 0,
    skipped_count   INT       NOT NULL DEFAULT 0,
    trigger_type    VARCHAR(20),          -- SCHEDULED | MANUAL
    triggered_by    UUID REFERENCES users(id) ON DELETE SET NULL,
    errors          TEXT                  -- newline-separated error messages; NULL = success
);

CREATE INDEX IF NOT EXISTS idx_sheet_sync_logs_synced_at
    ON sheet_sync_logs (synced_at DESC);

-- -----------------------------------------------------------------------------
-- Done
-- -----------------------------------------------------------------------------
-- Verify:
--   \d leads
--   \d sheet_sync_logs
-- =============================================================================
