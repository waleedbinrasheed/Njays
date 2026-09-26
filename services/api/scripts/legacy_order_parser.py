"""
One-time parser: turns the shop's legacy Excel ledger (Sheet1) into the JSON
payload expected by POST /api/v1/admin/import/legacy-orders.

Usage:
    python legacy_order_parser.py "C:\\path\\to\\SHOP DATA new.xlsx" output.json

The messy source structure (documented inline below) is grouped into one
logical order per customer/slip, with per-suit fabric lines nested under it.
See the accompanying import report printed to stdout for data-quality
warnings — review them before running the import, they are not silently
"fixed".
"""
import json
import re
import sys
from collections import Counter
from datetime import date, datetime

import openpyxl


def to_int(v):
    if v is None or v == "":
        return None
    try:
        return int(round(float(v)))
    except (ValueError, TypeError):
        return None


def to_float(v):
    if v is None or v == "":
        return None
    try:
        return float(v)
    except (ValueError, TypeError):
        return None


def parse_date(v):
    if v is None or v == "":
        return None
    if isinstance(v, (datetime, date)):
        return v.date().isoformat() if isinstance(v, datetime) else v.isoformat()
    s = str(v).strip()
    m = re.match(r"^(\d{1,2})-(\d{1,2})-(\d{4})$", s)
    if m:
        d, mo, y = m.groups()
        return f"{y}-{int(mo):02d}-{int(d):02d}"
    return None


def normalize_phone(raw):
    """Mirrors AuthService.normalizePhone, plus the one thing it can't infer:
    the ledger's phone numbers are missing their leading 0 entirely (Excel
    numeric-column artifact), so a bare 10-digit number starting with '3'
    needs '92' prepended here — AuthService.normalizePhone only rewrites
    numbers that already start with '0'. See
    LegacyOrderImportWorkerTest.javaNormalizePhoneDoesNotFixBareTenDigitNumbers
    for why this step cannot be skipped."""
    if not raw:
        return None
    digits = re.sub(r"[^0-9]", "", raw)
    if not digits:
        return None
    if digits.startswith("0"):
        return "92" + digits[1:]
    if len(digits) == 10 and digits.startswith("3"):
        return "92" + digits
    if digits.startswith("92"):
        return digits
    return digits  # left as-is; the API treats anything not exactly 12 digits starting with 92 as unusable


def parse_orders(path):
    wb = openpyxl.load_workbook(path, data_only=True)
    ws = wb["Sheet1"]

    def cell(r, c):
        return ws.cell(row=r, column=c).value

    orders = []
    current = None
    last_date = None

    for r in range(2, ws.max_row + 1):
        sno = cell(r, 1)
        name = cell(r, 3)
        row_date = parse_date(cell(r, 2))
        if row_date:
            last_date = row_date

        is_new_order = sno is not None or (name is not None and name != "")
        if is_new_order:
            if current:
                orders.append(current)
            current = {
                "rowStart": r,
                "customerName": str(name).strip() if name else "Customer",
                "phoneRaw": str(cell(r, 4)).strip() if cell(r, 4) is not None else None,
                "slipNumber": str(cell(r, 5)).strip() if cell(r, 5) is not None else None,
                "suitCount": to_int(cell(r, 6)),
                "branch": str(cell(r, 8)).strip() if cell(r, 8) is not None else None,
                "date": last_date,
                "items": [],
                "totalPkr": None,
                "paidPkr": None,
                "balancePkr": None,
                "status": None,
            }
        elif current is None:
            continue  # row with no order context at all; nothing to attach it to

        fabric = cell(r, 9)
        meter = to_float(cell(r, 10))
        rate = to_int(cell(r, 11))
        fabric_total = to_int(cell(r, 12))
        stitch = to_int(cell(r, 13))
        if fabric or meter or rate or fabric_total or stitch:
            current["items"].append({
                "fabricName": str(fabric).strip() if fabric else None,
                "meters": meter,
                "fabricRatePkr": rate,
                "fabricTotalPkr": fabric_total,
                "stitchPkr": stitch,
            })

        total = to_int(cell(r, 14))
        paid = to_int(cell(r, 15))
        balance = to_int(cell(r, 16))
        status = cell(r, 17)
        if total is not None:
            current["totalPkr"] = total
        if paid is not None:
            current["paidPkr"] = paid
        if balance is not None:
            current["balancePkr"] = balance
        if status:
            current["status"] = str(status).strip()

    if current:
        orders.append(current)
    return orders


def to_payload(orders):
    warnings = []
    payload_orders = []
    phone_counts = Counter()

    for o in orders:
        if o["totalPkr"] is None and o["paidPkr"] is not None:
            warnings.append(f"row {o['rowStart']} ({o['customerName']}): TOTAL missing, using PAID as total")
            o["totalPkr"] = o["paidPkr"]
        if o["totalPkr"] is not None and o["paidPkr"] is not None and o["balancePkr"] is not None:
            expected = o["totalPkr"] - o["paidPkr"]
            if expected != o["balancePkr"]:
                warnings.append(
                    f"row {o['rowStart']} ({o['customerName']}): total-paid={expected} but sheet balance={o['balancePkr']} (kept as-is)"
                )

        phone = normalize_phone(o["phoneRaw"])
        if phone and (len(phone) != 12 or not phone.startswith("92")):
            warnings.append(f"row {o['rowStart']} ({o['customerName']}): phone '{o['phoneRaw']}' -> '{phone}' is not a usable mobile number")
        if phone:
            phone_counts[phone] += 1

        legacy_ref = f"slip-{o['slipNumber']}" if o["slipNumber"] else f"row-{o['rowStart']}"

        items = [{
            "fabricName": it["fabricName"],
            "meters": it["meters"],
            "fabricRatePaisa": it["fabricRatePkr"] * 100 if it["fabricRatePkr"] is not None else None,
            "fabricTotalPaisa": it["fabricTotalPkr"] * 100 if it["fabricTotalPkr"] is not None else None,
            "stitchPaisa": it["stitchPkr"] * 100 if it["stitchPkr"] is not None else None,
        } for it in o["items"]]
        if not items:
            # stitch-only or otherwise itemless order: still needs one line so the
            # order has something to attach its total to.
            items = [{
                "fabricName": None, "meters": None, "fabricRatePaisa": None,
                "fabricTotalPaisa": None, "stitchPaisa": o["totalPkr"] * 100 if o["totalPkr"] else 0,
            }]

        payload_orders.append({
            "legacyRef": legacy_ref,
            "customerName": o["customerName"],
            "rawPhone": phone if phone else (o["phoneRaw"] or "unknown"),
            "slipNumber": o["slipNumber"],
            "suitCount": o["suitCount"],
            "branch": o["branch"],
            "orderDate": o["date"],
            "totalPaisa": (o["totalPkr"] or 0) * 100,
            "paidPaisa": (o["paidPkr"] or 0) * 100,
            "status": o["status"],
            "items": items,
        })

    repeat_customers = {p: c for p, c in phone_counts.items() if c > 1}
    print(f"Parsed {len(payload_orders)} orders, {len(phone_counts)} distinct phone-identified customers, {len(repeat_customers)} repeat customers.")
    print(f"{len(warnings)} data-quality warnings:")
    for w in warnings:
        print(" -", w)

    return {"orders": payload_orders}, warnings


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python legacy_order_parser.py <input.xlsx> <output.json>")
        sys.exit(1)
    orders = parse_orders(sys.argv[1])
    payload, warnings = to_payload(orders)
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)
    print(f"\nWrote {sys.argv[2]}")
