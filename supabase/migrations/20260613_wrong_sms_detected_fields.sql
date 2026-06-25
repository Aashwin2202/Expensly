-- Add detected transaction fields to wrong_sms reports.
-- Creates the table if it does not already exist, then adds the new columns.

CREATE TABLE IF NOT EXISTS wrong_sms (
    id          BIGSERIAL PRIMARY KEY,
    raw_sms     TEXT,
    sms_sender  TEXT,
    reason      TEXT,
    comments    TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE wrong_sms
    ADD COLUMN IF NOT EXISTS detected_merchant  TEXT,
    ADD COLUMN IF NOT EXISTS detected_amount    NUMERIC,
    ADD COLUMN IF NOT EXISTS detected_type      TEXT,
    ADD COLUMN IF NOT EXISTS detected_category  TEXT,
    ADD COLUMN IF NOT EXISTS detected_date      TEXT,
    ADD COLUMN IF NOT EXISTS detected_time      TEXT,
    ADD COLUMN IF NOT EXISTS detected_accounts  TEXT,
    ADD COLUMN IF NOT EXISTS detected_reference TEXT;
