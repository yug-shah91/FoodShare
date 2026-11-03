-- Initial schema (MySQL). Must match the entity classes User and Donation:
-- the "prod" profile runs with spring.jpa.hibernate.ddl-auto=validate and will refuse to start otherwise.
--
-- Never edit this file after it has been applied anywhere. To change the schema later, add a NEW file,
-- e.g. V2__add_something.sql (Flyway remembers which versions already ran).

CREATE TABLE users (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    username  VARCHAR(255) NOT NULL,
    password  VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role      ENUM ('DONOR', 'RECIPIENT', 'ADMIN') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE = InnoDB;

CREATE TABLE donations (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    food_name      VARCHAR(255) NOT NULL,
    category       ENUM ('COOKED_MEALS', 'BAKERY_ITEMS', 'VEGETABLES_FRUITS', 'DAIRY_PRODUCTS', 'PACKAGED_FOOD') NOT NULL,
    quantity       INT          NOT NULL,
    storage        ENUM ('ROOM_TEMPERATURE', 'REFRIGERATED', 'FROZEN') NOT NULL,
    address        VARCHAR(255) NOT NULL,
    notes          VARCHAR(255),
    allergens      VARCHAR(255),
    prepared_at    DATETIME(6)  NOT NULL,
    expires_at     DATETIME(6)  NOT NULL,
    status         ENUM ('ACTIVE', 'CLAIMED', 'COMPLETED', 'EXPIRED', 'CANCELLED') NOT NULL,
    donor_id       BIGINT       NOT NULL,
    claimed_by_id  BIGINT,
    claimed_at     DATETIME(6),
    claim_deadline DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_donations_donor   FOREIGN KEY (donor_id)      REFERENCES users (id),
    CONSTRAINT fk_donations_claimed FOREIGN KEY (claimed_by_id) REFERENCES users (id)
) ENGINE = InnoDB;

-- Indexes for the queries the app runs all the time (list pages sort by created_at DESC, id DESC)
CREATE INDEX idx_donations_status_created ON donations (status, created_at, id);
CREATE INDEX idx_donations_donor_created  ON donations (donor_id, created_at, id);
CREATE INDEX idx_donations_claimed_created ON donations (claimed_by_id, created_at, id);
CREATE INDEX idx_donations_expires        ON donations (expires_at);
