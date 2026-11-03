-- History of claims that were released because the recipient did not pick up in time.
CREATE TABLE claim_releases (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    donation_id    BIGINT      NOT NULL,
    recipient_id   BIGINT      NOT NULL,
    claimed_at     DATETIME(6) NOT NULL,
    claim_deadline DATETIME(6) NOT NULL,
    released_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_claim_releases_donation  FOREIGN KEY (donation_id)  REFERENCES donations (id),
    CONSTRAINT fk_claim_releases_recipient FOREIGN KEY (recipient_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_claim_releases_recipient ON claim_releases (recipient_id);
CREATE INDEX idx_claim_releases_donation  ON claim_releases (donation_id);
