-- ============================================================
-- Privacy-Safe SMS Pattern Reporter
-- ============================================================
-- Table stores anonymised SMS skeletons (no raw SMS, no user ID).
-- Each unique skeleton (identified by SHA-256 hash) has a hit counter
-- so rare patterns can be prioritised for new extraction rules.
-- ============================================================

-- Table ---------------------------------------------------------

CREATE TABLE IF NOT EXISTS unknown_patterns (
    pattern_hash  TEXT        PRIMARY KEY,          -- SHA-256 of skeleton_text
    skeleton_text TEXT        NOT NULL,             -- anonymised structural skeleton
    sender_id     TEXT        NOT NULL,             -- bank sender code, e.g. AX-HDFCBK
    hit_count     BIGINT      NOT NULL DEFAULT 1,
    last_detected TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index to quickly find high-frequency patterns
CREATE INDEX IF NOT EXISTS idx_unknown_patterns_hit_count
    ON unknown_patterns (hit_count DESC);

-- Index to find patterns from a specific sender
CREATE INDEX IF NOT EXISTS idx_unknown_patterns_sender
    ON unknown_patterns (sender_id);

-- Row-Level Security --------------------------------------------
-- Anon key can INSERT/UPDATE via the RPC below, but not SELECT raw rows.
ALTER TABLE unknown_patterns ENABLE ROW LEVEL SECURITY;

-- No direct SELECT for anon (data is internal tooling only)
CREATE POLICY "no_anon_select" ON unknown_patterns
    FOR SELECT USING (false);

-- RPC Function --------------------------------------------------
-- Called by the Android app using the Supabase anon key.
-- SECURITY DEFINER so it can bypass RLS for the upsert.

CREATE OR REPLACE FUNCTION increment_pattern_hit(
    p_hash     TEXT,
    p_skeleton TEXT,
    p_sender   TEXT
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO unknown_patterns (pattern_hash, skeleton_text, sender_id, hit_count, last_detected)
    VALUES (p_hash, p_skeleton, p_sender, 1, now())
    ON CONFLICT (pattern_hash) DO UPDATE SET
        hit_count     = unknown_patterns.hit_count + 1,
        last_detected = now();
END;
$$;

-- Grant execute to anon role (app uses anon key)
GRANT EXECUTE ON FUNCTION increment_pattern_hit(TEXT, TEXT, TEXT) TO anon;
