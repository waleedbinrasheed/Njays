-- Production payment hardening
ALTER TABLE payments ADD COLUMN failure_reason VARCHAR(512);
ALTER TABLE payments ADD COLUMN expires_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Make existing JazzCash refs unique before adding constraint
UPDATE payments
SET provider_ref = provider_ref || '-' || CAST(id AS VARCHAR)
WHERE provider_ref IS NOT NULL
  AND id IN (
      SELECT id FROM (
          SELECT id,
                 ROW_NUMBER() OVER (PARTITION BY provider_ref ORDER BY id DESC) AS rn
          FROM payments
          WHERE provider_ref IS NOT NULL
      ) ranked
      WHERE rn > 1
  );

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_provider_ref ON payments(provider_ref);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_method_status ON payments(method, status);
