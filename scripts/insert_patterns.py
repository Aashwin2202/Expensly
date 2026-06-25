#!/usr/bin/env python3
"""
insert_patterns.py
------------------
Upserts patterns from a JSON file into the sms_patterns Supabase table.

Usage:
    python3 scripts/insert_patterns.py                          # inserts patterns_ready.json
    python3 scripts/insert_patterns.py patterns_to_review.json  # any other file
"""

import json
import os
import sys
from pathlib import Path

from dotenv import load_dotenv
from supabase import create_client

load_dotenv()

DB_COLUMNS = [
    "regex", "regex_options", "transaction_type", "priority",
    "group_amount", "group_balance", "group_credit_limit", "group_currency", "group_date",
    "group_account", "group_merchant", "group_reference", "group_card_number", "group_bank_name",
    "account_label_type", "default_currency", "clean_merchant", "merchant",
    "sample_sms", "sender_id", "version",
]


def load_rows(path: Path) -> list[dict]:
    with open(path) as f:
        data = json.load(f)

    # Support both bare pattern lists and patterns_to_review.json (list of {pattern: {...}, ...})
    rows = []
    for entry in data:
        if "pattern" in entry and isinstance(entry["pattern"], dict):
            rows.append(entry["pattern"])
        elif "regex" in entry:
            rows.append(entry)
        else:
            print(f"  [WARN] Skipping unrecognised entry shape: {str(entry)[:80]}")
    return rows


def sanitise(row: dict) -> dict:
    out = {col: row.get(col) for col in DB_COLUMNS}
    out.setdefault("default_currency", "INR")
    out.setdefault("priority", 200)
    out.setdefault("version", 1)
    out.setdefault("clean_merchant", False)
    if isinstance(out.get("regex_options"), str):
        out["regex_options"] = [o.strip() for o in out["regex_options"].split(",") if o.strip()]
    return out


def upsert(client, rows: list[dict], batch_size: int = 50) -> None:
    total = 0
    for i in range(0, len(rows), batch_size):
        batch = rows[i: i + batch_size]
        client.table("sms_patterns").upsert(batch).execute()
        total += len(batch)
        print(f"  Upserted {total}/{len(rows)}...")
    print(f"Done. {total} pattern(s) inserted/updated.")


def main() -> None:
    scripts_dir = Path(__file__).parent
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else scripts_dir / "patterns_ready.json"
    if not target.is_absolute():
        target = scripts_dir / target

    if not target.exists():
        print(f"File not found: {target}")
        sys.exit(1)

    sb = create_client(os.environ["SUPABASE_URL"], os.environ["SUPABASE_SERVICE_ROLE_KEY"])

    raw = load_rows(target)
    if not raw:
        print("No rows found in file.")
        sys.exit(0)

    rows = [sanitise(r) for r in raw]
    print(f"Inserting {len(rows)} pattern(s) from {target.name}...")
    upsert(sb, rows)


if __name__ == "__main__":
    main()
