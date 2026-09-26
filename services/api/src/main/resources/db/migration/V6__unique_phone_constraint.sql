-- Phone numbers are now a valid login identifier alongside email, so they must
-- be unique the same way email already is. Guard against pre-existing
-- duplicates so this is safe to run against real data: keep the phone on the
-- oldest account that has it, clear it on any newer duplicates (nulling out,
-- not renumbering, since a mangled phone number is worse than a missing one —
-- those accounts simply fall back to email login until the user re-adds it).
UPDATE users
SET phone = NULL
WHERE phone IS NOT NULL
  AND id IN (
      SELECT id FROM (
          SELECT id,
                 ROW_NUMBER() OVER (PARTITION BY phone ORDER BY id ASC) AS rn
          FROM users
          WHERE phone IS NOT NULL
      ) ranked
      WHERE rn > 1
  );

ALTER TABLE users ADD CONSTRAINT uq_users_phone UNIQUE (phone);
