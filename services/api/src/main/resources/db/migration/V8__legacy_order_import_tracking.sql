-- Tracks orders backfilled from the shop's legacy paper/Excel ledger so the
-- import can be re-run safely without creating duplicates. NULL for every
-- normal order placed through the app.
ALTER TABLE orders ADD COLUMN legacy_ref VARCHAR(64) UNIQUE;
