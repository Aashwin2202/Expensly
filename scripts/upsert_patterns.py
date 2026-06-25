#!/usr/bin/env python3
"""
upsert_patterns.py
------------------
Inserts all rows from scripts/patterns_ready.json into the sms_patterns
Supabase table.  Rows have no 'id' field — Supabase auto-generates UUIDs.

Usage:
    python scripts/upsert_patterns.py
    python scripts/upsert_patterns.py --dry-run    # print rows, don't insert
"""

import json
import sys
import os
from pathlib import Path

from dotenv import load_dotenv
from supabase import create_client

load_dotenv()

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]

SUPABASE_COLUMNS = [
    "regex", "regex_options", "transaction_type", "priority",
    "group_amount", "group_balance", "group_credit_limit", "group_currency", "group_date",
    "group_account", "group_merchant", "group_reference", "group_card_number", "group_bank_name",
    "account_label_type", "default_currency", "clean_merchant", "merchant",
    "sample_sms", "sender_id", "version",
]


def sanitise(row: dict) -> dict:
    out = {col: row.get(col) for col in SUPABASE_COLUMNS}
    out.setdefault("default_currency", "INR")
    out.setdefault("priority", 200)
    out.setdefault("version", 1)
    if out.get("clean_merchant") is None:
        out["clean_merchant"] = False
    if isinstance(out.get("regex_options"), str):
        out["regex_options"] = [o.strip() for o in out["regex_options"].split(",") if o.strip()]
    if out.get("regex_options") is None:
        out["regex_options"] = ["IGNORE_CASE"]
    return out


def main():
    dry_run = "--dry-run" in sys.argv

    with open("patterns_ready.json") as f:
        patterns = json.load(f)

    rows = [sanitise(p) for p in patterns]
    print(f"Loaded {len(rows)} patterns from patterns_ready.json")

    if dry_run:
        print("DRY RUN — first 3 rows:")
        for r in rows[:3]:
            print(json.dumps(r, indent=2)[:400])
        return

    client = create_client(SUPABASE_URL, SUPABASE_KEY)

    batch_size = 50
    total = 0
    errors = 0
    for i in range(0, len(rows), batch_size):
        batch = rows[i: i + batch_size]
        try:
            client.table("sms_patterns").insert(batch).execute()
            total += len(batch)
            print(f"  Inserted {total}/{len(rows)}")
        except Exception as e:
            print(f"  ERROR on batch {i}-{i+len(batch)}: {e}")
            errors += len(batch)

    print(f"\nDone. Inserted: {total}  Errors: {errors}")


if __name__ == "__main__":
    main()
