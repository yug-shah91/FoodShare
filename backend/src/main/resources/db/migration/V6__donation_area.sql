-- The public listing shows only an approximate AREA; the exact address stays private (donor + claiming recipient).
-- Existing rows get a neutral placeholder; the donor can re-list with a real area.
ALTER TABLE donations ADD COLUMN area VARCHAR(100) NOT NULL DEFAULT 'Not specified';
