# SMS Patterns — Processing Workflow

## Overview

```
unknown_patterns (Supabase)
        ↓
  generate_patterns.py        ← clusters skeletons, calls Claude, validates regex
        ↓ (5 output files)
  Review each file manually
        ↓
  Upload to sms_patterns (Supabase)
        ↓
  verify_patterns.py          ← QA audit of patterns already in DB
```

---

## Step 1 — Run the generator

```bash
cd /path/to/FinTrackAI

# Standard run — all unprocessed rows, Haiku model
python scripts/generate_patterns.py

# Better quality — use for large or important batches
python scripts/generate_patterns.py --model claude-sonnet-4-6

# Test first with a small sample before committing
python scripts/generate_patterns.py --limit 50 --model claude-sonnet-4-6
```

**Optional — inspect clusters before spending API credits:**
```bash
python scripts/generate_patterns.py --dump-clusters
# Writes scripts/clusters_dump.json
# Review cluster groupings; tweak --eps if needed (default 0.15)
```

### Output files

| File | Contents | Your action |
|---|---|---|
| `patterns_ready.json` | Passed ≥90% validation | Review → upload |
| `patterns_to_review.json` | Failed validation | Fix regex → upload |
| `patterns_skipped_fake.json` | Filtered as promo/OTP/pending | Check for false positives |
| `patterns_split_suggestions.json` | Multi-bank clusters | Write per-bank patterns manually → upload |
| `patterns_errors.json` | Claude API errors (null response) | Re-run script — auto-retried |

---

## Step 2 — Review `patterns_ready.json`

These passed automated testing but still need a human check. Look for:

- **Wrong merchant label** — e.g. `"Cheque Payment"` on a `transaction_type: credit` → should be `"Cheque Credit"`
- **ATM location captured as merchant** — any skeleton with `withdrawn`/`withdrawal`/`atm wdl` where `group_merchant` is set → set `group_merchant: null`, `clean_merchant: false`, `merchant: "Withdrawal ({cardNumber})"` or `"Withdrawal ({account})"`
- **`clean_merchant: true` on a static label** — if `group_merchant` is null, `clean_merchant` must be false
- **Balance not captured** — if `sample_sms` contains `avl bal`/`new bal`/`bal inr` but `group_balance` is null, fix the regex and set `group_balance: "balance"`
- **Fixed codes captured as merchant** — `nach-ecs-*`, `bil*rchg*`, internal bank codes → use a static label (`"Dividend Credit"`, `"Bill Payment"`, etc.) with `group_merchant: null`

Upload when satisfied:
```bash
python scripts/generate_patterns.py --upload scripts/patterns_ready.json
```

---

## Step 3 — Fix `patterns_to_review.json`

These failed the ≥90% match rate. For each entry:

1. Read `cluster_key` and `sample_variants`
2. Mentally synthesise the SMS — replace placeholders with these concrete values:

| Placeholder | Example value | Note |
|---|---|---|
| `<AMOUNT>` | `1,234.56` | |
| `<BANK>` | `HDFC` | |
| `<ACCTNUM>` | `1234` | **digits only** — the `xx`/`xxxxxx` mask is already in the skeleton literal |
| `<CARDNUM>` | `5678` | **digits only** — same reason |
| `<NUM>` | `123456` | |
| `<DATE>` | `05-Jun-2025` | |
| `<TIME>` | `10:30:00` | |
| `<REF>` | `412345678901` | |
| `<VPA>` | `user@upi` | |
| `<MERCHANT>` | `Zomato` | |

3. Trace where the regex breaks. Common causes:

| Symptom | Root cause | Fix |
|---|---|---|
| `towards reversal/cashback` not matching | `(?:reversal\|cashback)` misses the slash form | `(?:reversal\/cashback\|reversal\|cashback)` |
| `bankName` eats the next word | `(?:\s+[A-Za-z]{2,10})?` suffix too greedy before a known literal like `credit card` | Remove the optional suffix — use `[A-Za-z]{2,20}` only |
| `.not you?` not matching | Regex has ` \.not` (space + dot) but SMS has `.not` directly after digits | Remove the space: `[\d:.\\/\-]+\.not` |
| `a/c xx\d+` fails on synth | `<ACCTNUM>` example was `xx1234` (old bug, now fixed) — if you see this in old files, the placeholder was double-prefixed | Retest with correct examples above |
| `ref#` breaks reference group | `ref#` has `#` which `[A-Za-z0-9]{6,30}` excludes | Add `(?:ref#\s+)?` optional prefix before the reference group |

4. Fix the `regex` field in the JSON, then verify:
```bash
python3 -c "
import re
sms = 'your synthesised SMS here'
m = re.search(r'your fixed regex here', sms, re.IGNORECASE)
print(m.groupdict() if m else 'NO MATCH')
"
```

Upload when all entries match:
```bash
python scripts/generate_patterns.py --upload scripts/patterns_to_review.json
```

---

## Step 4 — Check `patterns_skipped_fake.json`

The pre-filter is aggressive — some real transactions are caught. Look for:

- `reason: "no genuine transaction keyword found"` — read the skeleton carefully; if it's a real debit/credit where the keyword only appears in a variant (not the cluster key), it's a false positive
- `reason: "fake signal matched: 'cashback'"` — could be a genuine cashback reversal credit

Reprocess false positives bypassing the filter:
```bash
python scripts/generate_patterns.py --reprocess scripts/patterns_skipped_fake.json
```

The script prints a numbered menu — enter indices (e.g. `0,2`) or press Enter for all. Claude's own validity gate still runs inside the prompt, so genuinely fake entries get skipped again with a reason.

Output goes to `reprocess_ready.json` and `reprocess_to_review.json` — handle them the same as Steps 2 and 3:
```bash
python scripts/generate_patterns.py --upload scripts/reprocess_ready.json
python scripts/generate_patterns.py --upload scripts/reprocess_to_review.json  # after fixing
```

---

## Step 5 — Handle `patterns_split_suggestions.json`

These clusters span multiple banks whose SMS templates differ enough that one regex won't cover all. Write one pattern row per bank manually.

```bash
python3 -c "import json; [print(s['bank_code_map'], s['cluster_key'][:80]) for s in json.load(open('scripts/patterns_split_suggestions.json'))]"
```

For each entry:
1. Note `bank_code_map` — which banks are in the cluster
2. Check `sample_variants` — find where banks differ (account prefix length, balance format, fraud-tail wording)
3. Write one JSON object per bank following the `patterns_ready.json` schema
4. Set the correct `sender_id` for each bank
5. Save into a new file and upload:

```bash
python scripts/generate_patterns.py --upload scripts/patterns_manual.json
```

---

## Step 6 — Retry `patterns_errors.json`

Claude returned null for these clusters (rate limit or malformed batch). The script intentionally does **not** delete them from `unknown_patterns`, so they will be automatically retried on the next run:

```bash
python scripts/generate_patterns.py --model claude-sonnet-4-6
```

No manual action required.

---

## Step 7 — QA with `verify_patterns.py`

After uploading, audit patterns already in the DB against real SMS samples:

```bash
python scripts/verify_patterns.py
# Writes scripts/patterns_verification.json
```

Review `status` and `issue` fields. For any pattern that mismatches or captures the wrong merchant:
- Fix the `regex`/fields directly in `patterns_verification.json` (in the `pattern` key)
- Re-upload the corrected entry, or delete the bad row from Supabase and let it regenerate on the next run

---

## Full Checklist

```
[ ] Run generate_patterns.py (use --limit 50 to test first)
[ ] Review patterns_ready.json    — fix labels, merchant, balance → upload
[ ] Fix patterns_to_review.json   — trace each regex failure → upload
[ ] Check patterns_skipped_fake.json — reprocess false positives
[ ] Fix reprocess_to_review.json if any → upload
[ ] Write per-bank patterns for split_suggestions → upload
[ ] Re-run script to retry API errors
[ ] Run verify_patterns.py → fix anything flagged in patterns_verification.json
```

---

## Common Review Rules (quick reference)

**Merchant label rules:**
- `withdrawn`/`withdrawal`/`atm wdl` in skeleton → `merchant: "Withdrawal ({cardNumber})"` or `"Withdrawal ({account})"`, `group_merchant: null`
- `clg inst` + `transaction_type: credit` → `"Cheque Credit"`
- `clg inst` + `transaction_type: debit` → `"Cheque Payment"`
- `nach-ecs-final div` → `"Dividend Credit"`
- `salary credit`/`salary payment` → `"Salary"`
- No identifiable payee on a card POS → `"POS Transaction"`
- `neft`/`imps`/`rtgs` with beneficiary account → `"NEFT Transfer ({beneficiaryAccount})"` etc.

**`clean_merchant` rules:**
- `true` only when `group_merchant` is set (dynamic capture) — it title-cases and strips URL/handle noise at runtime
- `false` whenever `group_merchant` is null (static label)
- Never set `clean_merchant: true` on a fixed code like `nach-ecs-final div` or `bil*rchg*`

**`account_label_type` rules:**
- Set only when a `<BANK>` token appears alongside an account/card token
- `"Acct"` — savings/current account; `"Debit Card"` — debit card; `"Credit Card"` — credit card; `"Card"` — unknown card type
- `null` if no `<BANK>` token in skeleton
