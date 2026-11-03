-- The claim-history endpoint lists one recipient's releases newest first (WHERE recipient_id = ? ORDER BY released_at DESC, id DESC).
-- This index serves that query; it also covers lookups by recipient alone, so the old single-column index is redundant.
CREATE INDEX idx_claim_releases_recipient_released ON claim_releases (recipient_id, released_at, id);
DROP INDEX idx_claim_releases_recipient ON claim_releases;
