-- Admin-created walk-in customers (in-store point-of-sale orders) may not have
-- an email on file. The unique constraint stays — Postgres allows multiple
-- NULLs under a UNIQUE column, so this only relaxes the "must provide one" rule.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
