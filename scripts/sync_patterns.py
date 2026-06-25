#!/usr/bin/env python3
"""
sync_patterns.py
----------------
Syncs scripts/patterns_ready.json → sms_patterns Supabase table.

Behaviour:
  - Rows in file that match an existing DB row (by sample_sms) → UPDATE if any field changed
  - Rows in file with no matching DB row                        → INSERT (new pattern)
  - Rows in DB not present in file                             → left untouched

Usage:
    python scripts/sync_patterns.py
    python scripts/sync_patterns.py --dry-run     # show what would change, don't touch DB
    python scripts/sync_patterns.py --input scripts/some_other_file.json
"""

import argparse
import json
import os
import sys

from dotenv import load_dotenv
from supabase import create_client

load_dotenv("scripts/.env")

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


def fetch_all(client) -> list[dict]:
    rows = []
    offset = 0
    while True:
        batch = (
            client.table("sms_patterns")
            .select("id," + ",".join(SUPABASE_COLUMNS))
            .range(offset, offset + 999)
            .execute()
            .data
        )
        rows.extend(batch)
        if len(batch) < 1000:
            break
        offset += 1000
    return rows


def diff(file_row: dict, db_row: dict) -> dict:
    """Returns {field: new_value} for fields that differ between file and DB."""
    changes = {}
    for col in SUPABASE_COLUMNS:
        if col == "sample_sms":
            continue  # this is the match key, not a field to diff
        fv = file_row.get(col)
        dv = db_row.get(col)
        # Normalise: treat empty list same as None for regex_options comparison
        if isinstance(fv, list) and isinstance(dv, list):
            if sorted(fv) != sorted(dv):
                changes[col] = fv
        elif fv != dv:
            changes[col] = fv
    return changes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="Show changes without writing to DB")
    parser.add_argument("--input", default="scripts/patterns_ready.json", help="Input JSON file")
    args = parser.parse_args()

    with open(args.input) as f:
        raw = json.load(f)
    file_rows = [sanitise(p) for p in raw]
    print(f"Loaded {len(file_rows)} patterns from {args.input}")

    client = create_client(os.environ["SUPABASE_URL"], os.environ["SUPABASE_SERVICE_ROLE_KEY"])

    print("Fetching existing rows from DB...")
    db_rows = fetch_all(client)
    print(f"  {len(db_rows)} rows in DB")

    db_map = {r["sample_sms"]: r for r in db_rows if r.get("sample_sms")}

    to_update: list[tuple[str, dict]] = []   # (db_id, changed_fields)
    to_insert: list[dict] = []

    for row in file_rows:
        sms = row.get("sample_sms", "")
        if not sms:
            to_insert.append(row)
            continue
        db_row = db_map.get(sms)
        if db_row:
            changes = diff(row, db_row)
            if changes:
                to_update.append((db_row["id"], changes))
        else:
            to_insert.append(row)

    print(f"\nUpdates : {len(to_update)}")
    print(f"Inserts : {len(to_insert)}")
    print(f"No-ops  : {len(file_rows) - len(to_update) - len(to_insert)}")

    if args.dry_run:
        print("\n--- DRY RUN ---")
        for db_id, changes in to_update[:20]:
            print(f"  UPDATE {db_id[:8]}... → {list(changes.keys())}")
        for row in to_insert[:10]:
            print(f"  INSERT  {row.get('sample_sms', '')[:80]}")
        if len(to_update) > 20 or len(to_insert) > 10:
            print("  (truncated)")
        return

    if not to_update and not to_insert:
        print("\nDB is already in sync. Nothing to do.")
        return

    # Apply updates
    errors = 0
    if to_update:
        print(f"\nApplying {len(to_update)} updates...")
        for db_id, changes in to_update:
            try:
                client.table("sms_patterns").update(changes).eq("id", db_id).execute()
            except Exception as e:
                print(f"  ERROR updating {db_id}: {e}")
                errors += 1
        print(f"  Done ({len(to_update) - errors} updated, {errors} errors)")

    # Apply inserts in batches
    if to_insert:
        print(f"\nInserting {len(to_insert)} new rows...")
        batch_size = 50
        inserted = 0
        for i in range(0, len(to_insert), batch_size):
            batch = to_insert[i: i + batch_size]
            try:
                client.table("sms_patterns").insert(batch).execute()
                inserted += len(batch)
                print(f"  Inserted {inserted}/{len(to_insert)}")
            except Exception as e:
                print(f"  ERROR inserting batch {i}-{i+len(batch)}: {e}")
                errors += len(batch)

    print(f"\nSync complete. Updates: {len(to_update)}  Inserts: {len(to_insert)}  Errors: {errors}")


if __name__ == "__main__":
    main()
