-- Admin panel revamp: branches, dispatch cost, and order pricing additions

CREATE TABLE branches (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL UNIQUE,
    address     VARCHAR(255),
    phone       VARCHAR(32),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE dispatch_cost_rules (
    id                      BIGSERIAL PRIMARY KEY,
    source_branch_id        BIGINT NOT NULL REFERENCES branches(id),
    destination_branch_id   BIGINT NOT NULL REFERENCES branches(id),
    cost_paisa              BIGINT NOT NULL DEFAULT 0,
    UNIQUE (source_branch_id, destination_branch_id)
);

ALTER TABLE users ADD COLUMN branch_id BIGINT REFERENCES branches(id);

ALTER TABLE orders RENAME COLUMN shipping_paisa TO dispatch_cost_paisa;
ALTER TABLE orders ADD COLUMN discount_paisa BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN created_branch_id BIGINT REFERENCES branches(id);
ALTER TABLE orders ADD COLUMN dispatch_branch_id BIGINT REFERENCES branches(id);
ALTER TABLE orders ADD COLUMN expected_delivery_date DATE;

CREATE INDEX idx_orders_expected_delivery ON orders(expected_delivery_date);
CREATE INDEX idx_orders_created_branch ON orders(created_branch_id);
