# Legacy order import

One-time backfill of the shop's pre-app Excel ledger into the database.

## 1. Parse the spreadsheet into an import payload

Requires `openpyxl` (`pip install openpyxl`).

```bash
python legacy_order_parser.py "C:\path\to\SHOP DATA new.xlsx" legacy_import_payload.json
```

This prints a data-quality report (malformed phone numbers, sheet reconciliation
mismatches) — **read it before importing**. Nothing is silently "corrected"; the
sheet's own numbers are preserved as-is, and the report just tells you what's
inconsistent in the source data itself.

## 2. Log in as an admin to get a token

```bash
curl -s -X POST https://<your-api-host>/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"admin@menswear.local","password":"<your admin password>"}'
```

Copy the `accessToken` from the response.

## 3. Run the import

```bash
curl -s -X POST https://<your-api-host>/api/v1/admin/import/legacy-orders \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  --data @legacy_import_payload.json
```

The response reports `customersCreated`, `customersMatched`, `ordersCreated`,
`ordersSkipped`, and any per-order `warnings`.

**This is safe to run more than once** — each order carries a `legacyRef`
(derived from its slip number) that's checked before insert, so re-running the
same payload only fills in whatever didn't make it in last time; nothing is
duplicated.

## What each customer gets

- Matched by phone number where the ledger's phone is usable (exactly a
  Pakistani mobile number); otherwise a standalone customer record is created
  rather than risking merging unrelated people under one bad phone value.
- Password is their mobile number in local format (`03XXXXXXXXX`) — the same
  thing that's already displayed everywhere else in the app.
- New accounts have no email — that's fine for login by phone, but note the
  password-reset flow currently requires an email, so these customers can't
  self-service a password reset without one being added later.
