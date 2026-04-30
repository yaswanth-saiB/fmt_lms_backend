-- ═══════════════════════════════════════════════════════════════
-- Lead Management CRM — DB Migration Script
-- Run on PostgreSQL (fmt database) before deploying the new JAR
-- Safe to run on a fresh DB; use the IF NOT EXISTS guards for
-- re-runs (idempotent).
-- ═══════════════════════════════════════════════════════════════

-- ---------------------------------------------------------------
-- 1. leads table
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS leads (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(100)    NOT NULL,
    phone                   VARCHAR(20)     NOT NULL UNIQUE,
    email                   VARCHAR(100),
    course_interest         VARCHAR(100),
    source                  VARCHAR(50)     NOT NULL DEFAULT 'META_ADS',
    status                  VARCHAR(30)     NOT NULL DEFAULT 'NEW',
    dnp_count               INTEGER         NOT NULL DEFAULT 0,
    whatsapp_eligible       BOOLEAN         NOT NULL DEFAULT false,
    whatsapp_sent           BOOLEAN         NOT NULL DEFAULT false,
    followup_datetime       TIMESTAMP,
    assigned_to             UUID            REFERENCES users(id) ON DELETE SET NULL,
    notes                   TEXT,
    last_call_at            TIMESTAMP,
    current_level           VARCHAR(100),
    preferred_learning_mode VARCHAR(50),
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------
-- 2. lead_activities table
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lead_activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id         UUID        NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
    activity_type   VARCHAR(50) NOT NULL,
    description     TEXT,
    created_by      UUID        REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------
-- 3. Indexes (improve query performance for common patterns)
-- ---------------------------------------------------------------

-- Leads: filter by status (most common filter in the CRM)
CREATE INDEX IF NOT EXISTS idx_leads_status
    ON leads(status);

-- Leads: filter by assigned sales person
CREATE INDEX IF NOT EXISTS idx_leads_assigned_to
    ON leads(assigned_to);

-- Leads: paginated list ordered by updatedAt DESC
CREATE INDEX IF NOT EXISTS idx_leads_updated_at
    ON leads(updated_at DESC);

-- Leads: search by name (ILIKE '%query%' — not covered by btree,
--         but helps with equality lookups and sorting)
CREATE INDEX IF NOT EXISTS idx_leads_name
    ON leads(name);

-- Lead activities: fetch all activities for a lead (used on every detail view)
CREATE INDEX IF NOT EXISTS idx_lead_activities_lead_id
    ON lead_activities(lead_id);

-- Lead activities: count demo stats by type + timestamp (stats endpoint)
CREATE INDEX IF NOT EXISTS idx_lead_activities_type_created
    ON lead_activities(activity_type, created_at);

-- ---------------------------------------------------------------
-- 4. updated_at auto-update trigger
--    (Hibernate @UpdateTimestamp handles this from the app side,
--     but this trigger keeps it consistent for any direct SQL updates)
-- ---------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_leads_updated_at ON leads;
CREATE TRIGGER trg_leads_updated_at
    BEFORE UPDATE ON leads
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------
-- 5. NOTE on user_role column
--    The UserRole enum is stored as VARCHAR (EnumType.STRING).
--    Adding SALES to the Java enum is enough — no ALTER TYPE needed.
--    No DB change required for the role column itself.
-- ---------------------------------------------------------------

-- ---------------------------------------------------------------
-- 6. Verify (run these SELECTs to confirm tables were created)
-- ---------------------------------------------------------------
-- SELECT table_name FROM information_schema.tables
-- WHERE table_schema = 'public'
-- AND table_name IN ('leads', 'lead_activities');

-- SELECT column_name, data_type, character_maximum_length, column_default
-- FROM information_schema.columns
-- WHERE table_name = 'leads'
-- ORDER BY ordinal_position;
