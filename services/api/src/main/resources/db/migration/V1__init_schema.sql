-- Initial schema for menswear commerce MVP

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    phone           VARCHAR(32),
    role            VARCHAR(32)  NOT NULL DEFAULT 'CUSTOMER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE addresses (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    label           VARCHAR(64),
    line1           VARCHAR(255) NOT NULL,
    line2           VARCHAR(255),
    city            VARCHAR(128) NOT NULL,
    province        VARCHAR(128),
    postal_code     VARCHAR(32),
    country         VARCHAR(64)  NOT NULL DEFAULT 'PK',
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    slug            VARCHAR(128) NOT NULL UNIQUE,
    description     TEXT
);

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT       REFERENCES categories(id),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT,
    base_price_paisa BIGINT      NOT NULL,
    currency        VARCHAR(8)   NOT NULL DEFAULT 'PKR',
    supports_custom BOOLEAN      NOT NULL DEFAULT TRUE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE product_images (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url             VARCHAR(1024) NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    alt_text        VARCHAR(255)
);

CREATE TABLE fabric_tiers (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    surcharge_paisa BIGINT       NOT NULL DEFAULT 0,
    sort_order      INT          NOT NULL DEFAULT 0
);

CREATE TABLE fabric_colors (
    id              BIGSERIAL PRIMARY KEY,
    fabric_tier_id  BIGINT       NOT NULL REFERENCES fabric_tiers(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    hex_color       VARCHAR(16),
    UNIQUE (fabric_tier_id, code)
);

CREATE TABLE measurement_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    name            VARCHAR(128) NOT NULL,
    fit_type        VARCHAR(32)  NOT NULL DEFAULT 'SMART',
    unit            VARCHAR(8)   NOT NULL DEFAULT 'INCH',
    neck            NUMERIC(6,2),
    chest           NUMERIC(6,2),
    waist           NUMERIC(6,2),
    hip             NUMERIC(6,2),
    shoulder        NUMERIC(6,2),
    sleeve_length   NUMERIC(6,2),
    kameez_length   NUMERIC(6,2),
    shalwar_length  NUMERIC(6,2),
    notes           TEXT,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE carts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE REFERENCES users(id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE cart_items (
    id              BIGSERIAL PRIMARY KEY,
    cart_id         BIGINT       NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id      BIGINT       NOT NULL REFERENCES products(id),
    quantity        INT          NOT NULL DEFAULT 1,
    is_custom       BOOLEAN      NOT NULL DEFAULT FALSE,
    fabric_color_id BIGINT       REFERENCES fabric_colors(id),
    measurement_profile_id BIGINT REFERENCES measurement_profiles(id),
    unit_price_paisa BIGINT      NOT NULL,
    UNIQUE (cart_id, product_id, is_custom, fabric_color_id, measurement_profile_id)
);

CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    public_code     VARCHAR(32)  NOT NULL UNIQUE,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    order_type      VARCHAR(16)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    currency        VARCHAR(8)   NOT NULL DEFAULT 'PKR',
    subtotal_paisa  BIGINT       NOT NULL,
    shipping_paisa  BIGINT       NOT NULL DEFAULT 0,
    total_paisa     BIGINT       NOT NULL,
    shipping_address_json TEXT   NOT NULL,
    whatsapp_phone  VARCHAR(32),
    customer_note   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_phone ON orders(whatsapp_phone);

CREATE TABLE order_items (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      BIGINT       NOT NULL REFERENCES products(id),
    product_name    VARCHAR(255) NOT NULL,
    quantity        INT          NOT NULL,
    is_custom       BOOLEAN      NOT NULL DEFAULT FALSE,
    fabric_color_id BIGINT       REFERENCES fabric_colors(id),
    fabric_label    VARCHAR(255),
    measurement_json TEXT,
    unit_price_paisa BIGINT      NOT NULL,
    line_total_paisa BIGINT      NOT NULL
);

CREATE TABLE order_status_history (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status     VARCHAR(32),
    to_status       VARCHAR(32)  NOT NULL,
    note            TEXT,
    changed_by      BIGINT       REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES orders(id),
    method          VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    amount_paisa    BIGINT       NOT NULL,
    currency        VARCHAR(8)   NOT NULL DEFAULT 'PKR',
    provider_ref    VARCHAR(255),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    proof_url       VARCHAR(1024),
    raw_payload     TEXT,
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order ON payments(order_id);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
