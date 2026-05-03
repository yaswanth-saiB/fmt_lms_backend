-- ============================================================
-- Migration v4 — Allow recordings without a Zoom meeting
-- Run on EC2: psql -U fmtuser -d fmt -h localhost -f lead_crm_db_migration_v4.sql
-- ============================================================

-- Allow recording rows where no Zoom meeting exists (e.g. local/Google Drive uploads)
ALTER TABLE recordings ALTER COLUMN meeting_id DROP NOT NULL;

-- ============================================================
-- Done. Restart the app after running:
--   sudo systemctl restart fmt-backend
-- ============================================================
