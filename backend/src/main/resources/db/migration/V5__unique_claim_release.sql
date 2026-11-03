-- One claim (= one donation claimed at one moment) can be released only once. ExpiryService already guarantees
-- this with an atomic update; this key is the database-level safety net if two servers ever race.

-- Remove duplicates that an older version could have written (keeps the row with the lowest id).
DELETE r1 FROM claim_releases r1
    JOIN claim_releases r2
      ON r1.donation_id = r2.donation_id AND r1.claimed_at = r2.claimed_at AND r1.id > r2.id;

ALTER TABLE claim_releases ADD CONSTRAINT uk_claim_releases_claim UNIQUE (donation_id, claimed_at);
