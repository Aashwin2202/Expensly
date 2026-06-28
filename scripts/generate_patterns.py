#!/usr/bin/env python3
"""
generate_patterns.py
--------------------
Reads unknown_patterns from Supabase, clusters structurally identical skeletons,
calls Claude API to generate one sms_patterns row per cluster, validates each regex
against cluster members, and upserts passing rows into sms_patterns.

Requirements:
    pip install anthropic supabase python-dotenv

Environment variables (put in .env or export):
    SUPABASE_URL
    SUPABASE_SERVICE_ROLE_KEY   (service-role key — needed for direct table write)
    ANTHROPIC_API_KEY
"""

import argparse
import json
import os
import re
import time
from collections import defaultdict
from pathlib import Path

import anthropic
import numpy as np
from dotenv import load_dotenv
from rapidfuzz.distance import Levenshtein
from sklearn.cluster import DBSCAN
from supabase import create_client

load_dotenv()

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
ANTHROPIC_KEY = os.environ["ANTHROPIC_API_KEY"]

DEFAULT_MODEL = "claude-haiku-4-5-20251001"

# ---------------------------------------------------------------------------
# Constants derived from SmsAnonymizer / SmsConstants
# ---------------------------------------------------------------------------

# Every token the anonymizer can emit — these are NEVER the merchant
PLACEHOLDER_TOKENS = {
    "<AMOUNT>", "<BANK>", "<DATE>", "<TIME>",
    "<ACCTNUM>", "<CARDNUM>", "<REF>", "<VPA>", "<NUM>", "<MERCHANT>",
}

# Structural English stopwords + SMS-specific structural words that are never the merchant
STOPWORDS = {
    "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "be",
    "been", "being", "have", "has", "had", "do", "does", "did", "will", "would",
    "could", "should", "may", "might", "shall", "can", "need", "dare", "ought",
    "used", "to", "of", "in", "on", "at", "by", "for", "with", "about", "against",
    "between", "through", "during", "before", "after", "above", "below", "from",
    "up", "down", "out", "off", "over", "under", "again", "further", "then",
    "once", "if", "not", "no", "nor", "so", "yet", "both", "either", "neither",
    "each", "few", "more", "most", "other", "some", "such", "than", "too", "very",
    # SMS-specific structural words
    "rs", "inr", "bal", "balance", "avl", "lmt", "limit", "available",
    "amt", "amount", "bank", "card", "acct", "account", "debit", "credit",
    "sent", "paid", "spent", "debited", "credited", "withdrawn", "withdrawal",
    "transferred", "received", "deposited", "executed", "loaded", "deducted",
    "purchase", "transaction", "txn", "ref", "utr", "upi", "imps", "neft", "rtgs",
    "your", "you", "not", "call", "sms", "report", "block", "dial", "please",
    "if", "this", "info", "helpline", "contact", "reach", "write", "visit",
    "blkcc", "blkdc", "blk",
}

# Known bank names (from SmsConstants.BANK_NAMES, lowercased + extras)
BANK_NAMES = {
    "hdfc", "icici", "sbi", "axis", "kotak", "yes", "idfc", "pnb", "punjab",
    "rbl", "dbs", "hsbc", "citi", "boi", "indian", "utkarsh", "canara", "union",
    "central", "uco", "karnataka", "federal", "bandhan", "au", "jana", "equitas",
    "iob", "bob", "baroda", "south", "airtel", "paytm",
}

# Keywords that signal a false-positive <MERCHANT> (fraud-report contact, not a payee)
FRAUD_CONTACT_KEYWORDS = ("report to", "call", "sms", "dial", "block", "helpline", "contact")


# ---------------------------------------------------------------------------
# Step 1 — Fetch skeletons from Supabase
# ---------------------------------------------------------------------------

def fetch_unknown_patterns(supabase_client, limit: int | None = None) -> list[dict]:
    print("Fetching unknown_patterns from Supabase (unprocessed only)...")
    all_rows: list[dict] = []
    page_size = 1000
    offset = 0
    while True:
        fetch_size = page_size if limit is None else min(page_size, limit - len(all_rows))
        batch = (
            supabase_client
            .table("unknown_patterns")
            .select("pattern_hash,skeleton_text,sender_id,hit_count")
            .is_("processed_at", "null")
            .order("hit_count", desc=True)
            .range(offset, offset + fetch_size - 1)
            .execute()
            .data
        )
        all_rows.extend(batch)
        if len(batch) < fetch_size or (limit and len(all_rows) >= limit):
            break
        offset += page_size
    print(f"  Fetched {len(all_rows)} unprocessed rows")
    return all_rows


def stamp_processed(supabase_client, pattern_hashes: list[str]) -> None:
    """Mark rows as processed and delete them — keeps the table small."""
    if not pattern_hashes:
        return
    batch_size = 500
    deleted = 0
    for i in range(0, len(pattern_hashes), batch_size):
        batch = pattern_hashes[i: i + batch_size]
        supabase_client.table("unknown_patterns").delete().in_("pattern_hash", batch).execute()
        deleted += len(batch)
    print(f"  Deleted {deleted} processed rows from unknown_patterns")


# ---------------------------------------------------------------------------
# Step 2 — Cluster skeletons by edit distance (DBSCAN + rapidfuzz)
# ---------------------------------------------------------------------------

# Canonical single-char tokens for each placeholder — makes Levenshtein distance
# focus on structural words rather than the length of placeholder names.
_PLACEHOLDER_CANONICAL = {
    "<AMOUNT>":   "A",
    "<BANK>":     "B",
    "<DATE>":     "D",
    "<TIME>":     "T",
    "<ACCTNUM>":  "N",
    "<CARDNUM>":  "C",
    "<REF>":      "R",
    "<VPA>":      "V",
    "<NUM>":      "0",
    "<MERCHANT>": "M",
}

_PLACEHOLDER_RE = re.compile(
    "|".join(re.escape(k) for k in _PLACEHOLDER_CANONICAL),
    re.IGNORECASE,
)

# Tokens that appear before a fraud-report shortcode — the following <MERCHANT>/<NUM>
# is a hotline/shortcode, not a payee.
_FRAUD_CONTACT_RE = re.compile(
    r"(?:sms\s+block(?:\s+\w+)?\s+to|report\s+to|call|dial|helpline|not\s+you\?)"
    r"\s+(?:<MERCHANT>|<NUM>|\S+)",
    re.IGNORECASE,
)


def _strip_fraud_tail(skeleton: str) -> str:
    """
    Replace any fraud-report contact token with <HOTLINE> so it doesn't
    influence clustering or prompt Claude to capture it as a merchant.

    Patterns handled:
      sms block <NUM> to <MERCHANT>   →  sms block <NUM> to <HOTLINE>
      sms block <NUM> to <NUM>        →  sms block <NUM> to <HOTLINE>
      call <NUM> / dial <NUM>         →  call <HOTLINE> / dial <HOTLINE>
      report to <MERCHANT>            →  report to <HOTLINE>
    """
    # "sms block ... to <MERCHANT|NUM>" — the token after "to" is the shortcode
    s = re.sub(
        r"(sms\s+block\b[^t]*?\bto\s+)(?:<MERCHANT>|<NUM>|\S+)",
        r"\1<HOTLINE>",
        skeleton,
        flags=re.IGNORECASE,
    )
    # "call <NUM>" / "dial <NUM>" standalone hotline numbers
    s = re.sub(
        r"(\b(?:call|dial)\s+)(?:<NUM>|\d[\d\s-]{4,})",
        r"\1<HOTLINE>",
        s,
        flags=re.IGNORECASE,
    )
    # "report to <MERCHANT|NUM>"
    s = re.sub(
        r"(report\s+to\s+)(?:<MERCHANT>|<NUM>)",
        r"\1<HOTLINE>",
        s,
        flags=re.IGNORECASE,
    )
    return s


def _normalise_for_distance(skeleton: str) -> str:
    """
    Collapse each placeholder to a single char so edit distance reflects
    structural differences, not placeholder name lengths.
    Also lowercase and collapse whitespace.
    Fraud-report tail tokens are neutralised before comparison.
    """
    s = _strip_fraud_tail(skeleton).strip().lower()
    s = _PLACEHOLDER_RE.sub(lambda m: _PLACEHOLDER_CANONICAL.get(m.group().upper(), "?"), s)
    # <HOTLINE> → "H" (same neutral char regardless of original token)
    s = re.sub(r"<hotline>", "H", s)
    s = re.sub(r"\s+", " ", s)
    return s


def cluster_skeletons(rows: list[dict], eps: float = 0.15) -> dict[str, list[dict]]:
    """
    Groups skeletons by structural similarity using DBSCAN over normalised
    Levenshtein distance. eps controls how similar two skeletons must be to
    land in the same cluster (0.15 = ≤15% edit distance, character-level).

    The cluster key is the skeleton_text of the highest-hit member.
    """
    n = len(rows)
    normalised = [_normalise_for_distance(r["skeleton_text"]) for r in rows]

    total_pairs = n * (n - 1) // 2
    print(f"  Computing {n}×{n} distance matrix ({total_pairs:,} pairs)...", flush=True)
    dist = np.zeros((n, n), dtype=np.float32)

    done = 0
    t_start = time.time()
    bar_width = 40
    for i in range(n):
        for j in range(i + 1, n):
            d = Levenshtein.normalized_distance(normalised[i], normalised[j])
            dist[i, j] = d
            dist[j, i] = d
            done += 1

        # Update progress after each row (n-1 updates total — cheap enough)
        elapsed = time.time() - t_start
        pct = done / total_pairs
        eta = (elapsed / pct - elapsed) if pct > 0 else 0
        filled = int(bar_width * pct)
        bar = "█" * filled + "░" * (bar_width - filled)
        print(f"\r  [{bar}] {pct:5.1%}  elapsed {elapsed:5.1f}s  ETA {eta:5.1f}s", end="", flush=True)

    elapsed = time.time() - t_start
    print(f"\r  [{'█' * bar_width}] 100.0%  done in {elapsed:.1f}s{' ' * 20}", flush=True)

    print(f"  Running DBSCAN (eps={eps})...", flush=True)
    labels = DBSCAN(eps=eps, min_samples=1, metric="precomputed").fit_predict(dist)

    # Group rows by label
    label_to_indices: dict[int, list[int]] = defaultdict(list)
    for idx, label in enumerate(labels):
        label_to_indices[label].append(idx)

    # Build clusters: key = skeleton_text of highest-hit member
    clusters: dict[str, list[dict]] = {}
    for label, indices in label_to_indices.items():
        members = [rows[i] for i in indices]
        best = max(members, key=lambda r: r["hit_count"])
        clusters[best["skeleton_text"]] = members

    # Sort by total hits descending
    sorted_clusters = dict(
        sorted(clusters.items(), key=lambda kv: sum(r["hit_count"] for r in kv[1]), reverse=True)
    )
    print(f"  {n} skeletons → {len(sorted_clusters)} clusters (eps={eps})")
    return sorted_clusters


# ---------------------------------------------------------------------------
# Step 3 — Claude prompt + API call
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Pre-flight: skip skeletons that are not genuine completed transactions
# ---------------------------------------------------------------------------

# Signals that a skeleton is promotional / informational, not a real transaction
FAKE_TRANSACTION_SIGNALS = [
    # Cashback / reward offers
    re.compile(r"\bcashback\b", re.IGNORECASE),
    re.compile(r"\bcash\s*back\b", re.IGNORECASE),
    re.compile(r"\b(?:earn|get|win|claim)\b.{0,60}\b(?:reward|point|bonus|offer|voucher|discount)\b", re.IGNORECASE | re.DOTALL),
    re.compile(r"\breward\s*point", re.IGNORECASE),
    # Offer / promotion language
    re.compile(r"\boffer\s+(?:valid|expires?|ends?|till|until)\b", re.IGNORECASE),
    re.compile(r"\bt\s*&\s*c\b", re.IGNORECASE),          # T&C apply
    re.compile(r"\bapply\s+now\b", re.IGNORECASE),
    re.compile(r"\bclick\s+(?:here|to)\b", re.IGNORECASE),
    re.compile(r"\bvisit\s+(?:our|the|www)\b", re.IGNORECASE),
    # Future/conditional spend — not a completed transaction
    re.compile(r"\bjust\s+spend\b", re.IGNORECASE),
    re.compile(r"\bspend\s+(?:rs|inr|₹|<AMOUNT>)\b", re.IGNORECASE),
    re.compile(r"\bon\s+(?:fuel|grocery|shopping|dining|travel)\s+purchases?\b", re.IGNORECASE),
    re.compile(r"\bawaits?\s+you\b", re.IGNORECASE),
    # OTP / login
    re.compile(r"\botp\b", re.IGNORECASE),
    re.compile(r"\bone[- ]time\s+password\b", re.IGNORECASE),
    re.compile(r"\bdo\s+not\s+share\b", re.IGNORECASE),
    # Bill reminders / statements (not completed transactions)
    re.compile(r"\bdue\s+(?:on|by|date)\b", re.IGNORECASE),
    re.compile(r"\bmin(?:imum)?\s+(?:amount\s+)?due\b", re.IGNORECASE),
    re.compile(r"\boutstanding\b.{0,60}\bdue\b", re.IGNORECASE | re.DOTALL),
    # Pending / scheduled
    re.compile(r"\bwill\s+be\s+(?:debited|credited|processed)\b", re.IGNORECASE),
    re.compile(r"\bscheduled\s+(?:on|for)\b", re.IGNORECASE),
    re.compile(r"\bplease\s+ensure\s+sufficient\s+balance\b", re.IGNORECASE),
    # Mandate registration (not a debit event)
    re.compile(r"\bmandate\s+(?:registered|created|approved|set\s+up)\b", re.IGNORECASE),
    # Mandate / collect REQUESTS — authorisation pending, NO money has moved yet.
    # e.g. "you have received a upi-mandate request from X for rs Y. authorize the block"
    #      "upi-mandate collect request from X ... open app to authorize"
    re.compile(r"\bupi-?mandate\b", re.IGNORECASE),
    re.compile(r"\bmandate\s+(?:collect\s+)?request\b", re.IGNORECASE),
    re.compile(r"\bcollect\s+request\b", re.IGNORECASE),
    re.compile(r"\b(?:authori[sz]e|approve)\s+(?:the\s+)?(?:block|mandate|request|payment|debit)\b", re.IGNORECASE),
    re.compile(r"\bto\s+authori[sz]e\b", re.IGNORECASE),
    re.compile(r"\bauthori[sz]e\s+the\s+block\b", re.IGNORECASE),
    # Generic payment/collect requests (awaiting user action, not completed)
    re.compile(r"\b(?:payment|collect)\s+request\s+(?:from|of|for)\b", re.IGNORECASE),
    re.compile(r"\bhas\s+requested\s+(?:rs|inr|₹|money|payment)\b", re.IGNORECASE),
    re.compile(r"\brequest\s+(?:to\s+pay|for\s+payment)\b", re.IGNORECASE),
    # Failed transactions
    re.compile(r"\b(?:declined|failed|unsuccessful|could\s+not\s+be\s+processed)\b", re.IGNORECASE),
]

# Signals that confirm a skeleton IS a genuine completed transaction
GENUINE_TRANSACTION_SIGNALS = [
    re.compile(r"\b(?:debited|credited|withdrawn|transferred|paid|sent|spent|received|deposited|executed|loaded|deducted)\b", re.IGNORECASE),
]


def classify_skeleton(skeleton: str) -> tuple[bool, str]:
    """
    Returns (is_genuine, reason).
    A skeleton is genuine if it has at least one completed-transaction signal
    and no promotional/fake signals.
    """
    for pattern in FAKE_TRANSACTION_SIGNALS:
        m = pattern.search(skeleton)
        if m:
            return False, f"fake signal matched: '{m.group()}'"

    for pattern in GENUINE_TRANSACTION_SIGNALS:
        if pattern.search(skeleton):
            return True, "genuine"

    # Has neither — ambiguous; treat as not genuine to avoid bad patterns
    return False, "no genuine transaction keyword found"


SYSTEM_PROMPT = """You are an expert at writing Android SMS transaction-parsing regexes for the FinTrackAI app.
Your task: given an anonymised SMS skeleton and variant examples, produce one JSON object describing
a regex pattern row for the sms_patterns Supabase table.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0 — VALIDITY GATE (do this FIRST, before anything else)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Before writing any regex, decide: is this skeleton a COMPLETED money-movement
transaction (money has actually left or entered the user's account)?

A pattern row should ONLY be produced for completed transactions. If the skeleton
is anything below, it is NOT a valid transaction — return exactly:
    {"skip": true, "skip_reason": "<short reason>"}
and nothing else for that cluster.

NOT valid transactions (skip these):
  • UPI-mandate / e-mandate / collect REQUESTS — "you have received a upi-mandate
    request from X for rs Y … authorize the block", "collect request from X …
    open app to authorize". These are authorisation requests for a FUTURE debit.
    No money has moved. SKIP.
  • Any message asking the user to "authorize", "approve", "authorise the block",
    or "open … app to authorize" — action is pending, not completed. SKIP.
  • Payment requests / money requests — "X has requested rs Y", "request to pay". SKIP.
  • Mandate registered / created / approved / set up (registration only). SKIP.
  • Scheduled / future / pending — "will be debited", "scheduled on", "auto debit
    as scheduled", "ensure sufficient balance". SKIP.
  • Bill reminders / statements / due dates / minimum amount due. SKIP.
  • Failed / declined / unsuccessful / reversed-pending transactions. SKIP.
  • OTP / login / promotional / cashback / reward / offer messages. SKIP.

VALID transactions (produce a row): the money has already moved — "debited",
"credited", "withdrawn", "spent", "paid", "sent", "received", "deposited",
"transferred", "deducted", "loaded" — describing an event that HAS happened.

Caution: the word "received" alone does NOT make it valid. "received a request"
/ "received a upi-mandate" is a request, not a receipt of money — SKIP those.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CRITICAL: GROUP NAME FORMAT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Named groups MUST be camelCase with NO underscores. The Android Kotlin regex engine
rejects underscores in group names and will silently fail to match.
Use (?<groupName>...) syntax — NOT (?P<groupName>...) which is Python-style.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SKELETON PLACEHOLDERS → REGEX FRAGMENTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Use exactly these group names and fragments:

  <AMOUNT>   → (?<amount>[\d,]+(?:\.\d{1,2})?)
               Currency prefix (rs., inr, ₹) is OUTSIDE the group, matched literally.
               BALANCE DETECTION — an ACCOUNT balance after the transaction.
               Trigger words: "bal", "balance", "avl bal", "avl. bal", "available balance",
               "ac bal", "acbal", "clrbal", "closing bal", "ledger bal", "cr bal", "dr bal",
               "new bal", "avbl bal", "avb bal", "outstanding bal", "total avail".
               If an <AMOUNT>/<NUM> token is preceded/followed by any of these words,
               capture it as (?<balance>[\d,]+(?:\.\d{1,2})?) and set group_balance="balance".

               CREDIT LIMIT DETECTION — the AVAILABLE CREDIT LIMIT on a card. This is a
               DIFFERENT concept from account balance — use a SEPARATE group/field.
               Trigger words: "avl lmt", "available limit", "available credit limit",
               "avl. credit limit", "credit limit is", "cr limit", "available cr limit".
               If an <AMOUNT>/<NUM> token follows any of these, capture it as
               (?<creditLimit>[\d,]+(?:\.\d{1,2})?) and set group_credit_limit="creditLimit".
               Do NOT put a credit limit into group_balance, and vice-versa.
               Note: "cash limit" is neither — ignore it (no group).

               Each group name must be unique; never reuse "amount" for a balance/limit token.
               If TWO <AMOUNT> tokens appear and the second follows a balance/limit keyword:
                 first  = (?<amount>...)       → group_amount="amount"
                 second = (?<balance>...)      → group_balance="balance"        (account bal)
                       OR (?<creditLimit>...)  → group_credit_limit="creditLimit" (card limit)

  <BANK>     → (?<bankName>[A-Za-z]{2,20}(?:\s+[A-Za-z]{2,10})?)
               group_bank_name = "bankName"

  <DATE>     → (?<date>\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4}|\d{4}[\/\-]\d{2}[\/\-]\d{2}|\d{1,2}[\/\-](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\/\-]\d{2,4})
               group_date = "date"

  <TIME>     → \d{1,2}:\d{2}(?::\d{2})?(?:\s*[AP]M)?
               NO named group. Do NOT set group_date for this.

  <ACCTNUM>  → (?<account>[Xx*\d]{4,20})
               group_account = "account"

  <CARDNUM>  → (?<cardNumber>[Xx*\d]{4,20})
               group_card_number = "cardNumber"
               NOTE: "cardNumber" and "account" are mutually exclusive — use whichever the skeleton shows.

  <REF>      → (?<reference>[A-Za-z0-9]{6,30})
               group_reference = "reference"

  <VPA>      → (?<merchant>[a-zA-Z0-9.\-_]+)@[a-zA-Z0-9]+
               group_merchant = "merchant", clean_merchant = true
               Capture only the username before @; the bank handle (@oksbi, @goaxb, etc.) is structural noise.

  <MERCHANT> → The anonymizer tagged this word as a possible merchant, but it MAY be wrong.
               Treat <MERCHANT> as a hint only — do NOT automatically capture it.
               Apply the MERCHANT DECISION section below to determine the correct action.

  <NUM>      → \d+   — NO named group
             EXCEPTION 1 (MASKED ACCOUNT — capture it, do NOT discard as \d+):
             if <NUM> appears after a masking prefix OR near an account word, that <NUM> is
             the masked account suffix → use (?<account>\d+), group_account="account",
             account_label_type="Acct". This fires for ALL of these shapes:
               "xx<NUM>", "xxx<NUM>", "nn<NUM>" (IDBI), "sb-xxx<NUM>", "ac-xxx<NUM>",
               "acc xx<NUM>", "a/c xx<NUM>", "account xx<NUM>", "ac:xxx<NUM>",
               "a/c *<NUM>", 'a/c "****<NUM>"', "<NUM>xx<NUM>" (last group is the account),
               and "ending with <NUM>" when preceded by a/c|account.
             A bare "xx<NUM>" sitting next to "a/c"/"acc"/"account"/"bank ... <NUM>" is the
             account number — never leave group_account null in that case.
             EXCEPTION 2 (CARD PREFIX): if <NUM> appears immediately BEFORE "xxx" or "xxxxxx"
             followed by <CARDNUM> (e.g. "<NUM>xxxxxx<CARDNUM>"), that <NUM> is the visible card
             prefix digits — match as \d+ with NO named group, then the x's literally, then
             capture (?<cardNumber>\d+).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
REGEX CONSTRUCTION RULES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Non-token literal text: escape special regex chars (., (, ), *, +, ?, [, ], /, -) with \\.
2. Between tokens: use [\\s\\S]*? (lazy wildcard) unless the skeleton shows a fixed separator,
   in which case match that separator literally.
3. Only define a named group for a token if it appears in the regex. Set the corresponding
   group_* field to the exact group name string; set all unused group_* fields to null.
4. regex_options: always ["IGNORE_CASE"] unless the skeleton has explicit case-sensitive tokens.
5. priority: 1.0 for specific patterns (few wildcards), 5.0 for generic ones. Lower = tried first.
6. BARE LITERAL MERCHANT WORDS: If the skeleton contains a concrete merchant/payee name as a
   plain word (not a placeholder) — e.g. "at swiggy", "to zomato", "at pyu*swiggy food" — do NOT
   hardcode that word literally. The anonymizer missed it. Instead use a merchant capture group
   in its place: (?<merchant>[A-Za-z0-9 *&'._-]{2,50})
   Trigger words that introduce a bare merchant: "at", "to", "for", "paid to", "trf to", "@", "@upi_".
   Set group_merchant="merchant", clean_merchant=true.

   NAMED STRUCTURAL PATTERNS — these shapes ALWAYS need a capture group, never a static string,
   because the merchant name between the anchors varies per transaction:

   a) "towards <NAME> umrn:"  /  "towards <NAME> for upi mandate"
      The NAME is a fund/utility/company — it varies (motilal oswal, indian clearing corp,
      pm kisan, netflix, etc.). Match: towards\s+(?<merchant>[A-Za-z0-9 &._/-]+?)\s+(?:umrn:|for\s+(?:upi\s+)?mandate)
      Do NOT convert NAME to a static string.

   b) "<NAME> bill of rs"  /  "your <NAME> bill amount"
      The word(s) before "bill" is the utility/service name — it varies (adani gas, bjali,
      electricity, broadband, etc.). Match: (?<merchant>[A-Za-z0-9 &._-]+?)\s+bill\s+(?:of\s+rs|amount\s+of)
      Do NOT convert NAME to a static string.
7. FUSED DATE+TIME TOKENS: If the skeleton shows <NUM>-<NUM>-<TIME> or similar mixed sequences
   where the anonymizer split a timestamp into multiple tokens, match the whole thing as a single
   flexible fragment: [\d:.\-\/]+ — do NOT try to parse the sub-parts individually.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EXTRACTION COMPLETENESS — DO NOT LEAVE A FIELD NULL IF IT IS RECOVERABLE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
A field defaulting to null is a BUG when the information is sitting in the message.
For every row, run this checklist and recover what you can:

MERCHANT — if group_merchant would be null:
  Re-read the message and look for ANY variable payee/counterparty name. Prefer a
  capture group over a static string whenever the name would differ across
  transactions of this type. Common recoverable spots (capture, set
  group_merchant="merchant", clean_merchant=true, merchant=null):
    • "by sender <NAME>,"            (NEFT/RTGS credit)   → by sender (?<merchant>[^,]+?),
    • "credited in <NAME> bank account"  (Paytm credit)   → credited in (?<merchant>[\\s\\S]+?) bank account
    • "from <NAME> <DATE>"           (ACH/deposit credit) → from (?<merchant>[A-Za-z0-9 &'.\\-]{2,50}?) <date>
    • "credited to <BANK> account <NAME> on"              → account (?<merchant>[A-Za-z][A-Za-z ]{1,40}?) on
    • "for payee <NAME> for rs"      (IOB debit)          → for payee (?<merchant>[A-Za-z0-9 &'._/\\-]{2,60}) for
    • "ach d- <NAME>-<code>"         (auto-debit/SIP)     → ach d-\\s*(?<merchant>[A-Za-z0-9 &'._-]{2,50})-
    • "info: clg*<NAME>*<NUM>"       (cheque clearing)    → clg\\*(?<merchant>[A-Za-z0-9 &'.\\-]{2,50})\\*
    • "fd through bbsmrt-...:<NAME>"  (FD)                 → :(?<merchant>[A-Za-z0-9 &'.\\-]{2,50})\\.
    • "@<handle>"                     (VPA/UPI handle)     → @(?<merchant>[A-Za-z0-9._-]+)
    • "ref refund-<NAME>-<NUM>", "vip/vie-N-N/N-<NAME>", "to <NAME> on/ ref/ application no."
  Only fall back to a static label / null AFTER confirming no variable name exists.
  Generic labels are bare strings (e.g. "Salary", "POS Transaction", "EMI Payment").
  Append a {placeholder} ONLY for ATM withdrawals ("Withdrawal ({account})") and
  account-to-account transfers ("NEFT Transfer ({beneficiaryAccount})"). See MERCHANT
  DECISION (b).

ACCOUNT / CARD — if BOTH group_account and group_card_number would be null:
  Look for a masked account or card number anywhere in the message and capture it.
  The masked suffix IS the account/card — recover it (set group_account="account"
  or group_card_number="cardNumber", account_label_type="Acct"/"Card" as fits):
    • "bank xx<NUM>", "acc xx<NUM>", "a/c xx<NUM>"   → xx(?<account>\\d+)
    • "a/c nn<NUM>" (IDBI)                            → nn(?<account>\\d+)
    • "a/c *<NUM>", 'a/c \"****<NUM>\"'               → \\*+(?<account>\\d+)
    • "ending with <NUM>", "ac:xxx<NUM>", "sb-xxx<NUM>" → ...(?<account>\\d+)
    • "<NUM>xxxxxx<CARDNUM>"  → visible-prefix \\d+, x's literal, (?<cardNumber>\\d+)
  A bare "xx<NUM>" / "nn<NUM>" near "a/c", "acc", "account", "card" is NOT noise —
  it is the masked number. Do not match it as plain \\d+ and discard it.

BALANCE — if group_balance would be null:
  Scan for a balance keyword followed by an amount and capture it (see <AMOUNT>
  BALANCE DETECTION). The classic case is TWO amounts where the FIRST is the txn
  amount and the SECOND follows "avl bal"/"bal inr"/"new bal"/"avb bal"/
  "available balance". Name the second (?<balance>...).
  Do NOT leave the trailing balance amount as an unnamed [\\d,]+ literal — name it.

CREDIT LIMIT — if group_credit_limit would be null:
  On credit-card SMS, look for "available credit limit"/"avl lmt"/"credit limit is"
  followed by an amount and capture it as (?<creditLimit>...), group_credit_limit="creditLimit".
  Keep it OUT of group_balance — a card's available limit is not an account balance.

DATE / REFERENCE / BANK — likewise capture <DATE>, <REF>, and trailing
"- <BANK> bank" signatures rather than skipping or hardcoding them.

This checklist is mandatory: a null on group_merchant, group_account,
group_card_number, group_balance, or group_credit_limit is only acceptable when you have verified the
underlying information is genuinely absent from the message.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MERCHANT DECISION (read the full SMS context first)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Ignore the <MERCHANT> tag. Instead, read the full skeleton and ask:
"Who or what is the actual payee/counterparty in this transaction?"

Bias toward a CAPTURE GROUP. A dynamic (?<merchant>...) is almost always better than a
static string — prefer it whenever a real name appears in the message, even if the
anonymizer hardcoded it as a plain word. Only choose static/template/null after the
EXTRACTION COMPLETENESS checklist confirms no variable name exists.

RECOGNITION IS NOT A REASON TO USE STATIC. Even if you recognise the merchant name
(Netflix, Amazon, IIT Guwahati, Motilal Oswal, Adani Gas) — that does NOT mean you
should hardcode it. Ask: "Could a different transaction of this structural type have
a different payee here?" If yes → capture group. Static is only correct when the
specific name is baked into the SMS template itself and cannot vary (e.g. a bank's own
UPI handle, or a government scheme identifier that never changes across any bank's SMS).

CRITICAL — VARYING WORDS: The input for each cluster may include a "VARYING WORDS" note
listing literal words that differ across variants. Any word in that list MUST become a
(?<merchant>...) capture group in your regex — never hardcode it literally. If the cluster
key contains "bajaj broking", "anushka foods", "airport lounge", "ketto", "airbnb", etc.
and those words are flagged as varying — replace them with (?<merchant>[A-Za-z0-9 &._-]{2,60}).
Even without the note: if a business name appears as a plain word in the skeleton (not as a
<MERCHANT> placeholder), treat it as varying unless ALL variants share it exactly.

Then pick ONE of these, in order:

a) VARYING PAYEE — the payee differs across variants (e.g. "paid to Zomato", "paid to Amazon"),
   OR a concrete name appears once (e.g. "by sender lic ajmer do", "for payee fnp e retail",
   "credited in nazim's bank account") — treat single-occurrence names as varying too.
   Use a capture group: (?<merchant>[A-Za-z0-9 &'.\\-]{2,50})
   Set group_merchant="merchant", clean_merchant=true, merchant=null.

b) STATIC LABEL — no variable payee name, so the "merchant" is a generic label for the
   transaction type. Use a plain, bare string (NO placeholder):
     "Salary", "POS Transaction", "Card Purchase", "EMI Payment", "Interest Payout",
     "Fund Transfer", "Net Banking Transfer", "Online Payment", "Cheque Payment",
     "Cheque Credit", "Insurance Payout", "Bank Debit", etc.
   CHEQUE CREDIT vs CHEQUE PAYMENT — check transaction_type first:
     • transaction_type="credit" + "clg inst" / "clearing" / "chq" / "cheque" in skeleton
       → merchant="Cheque Credit"  (an inbound cheque cleared into the account)
     • transaction_type="debit"   + cheque indicator
       → merchant="Cheque Payment" (a cheque written by the user to pay someone)
   Set group_merchant=null, clean_merchant=false, merchant="<bare label>".

   WITHDRAWAL DETECTION — check this BEFORE deciding on a merchant capture group:
   If the skeleton starts with or contains any of these trigger phrases, the transaction
   is an ATM / cash withdrawal — there is NO payee merchant, only a location "at <place>".
   The "at <place>" fragment is the ATM location, NOT a payee — do NOT capture it as merchant.
   Match the location as a plain non-capturing literal: [A-Za-z0-9 &'._*+\-]{2,60}

   Withdrawal triggers (any of these present = withdrawal):
     "withdrawn", "withdrawal", "atm wdl", "nfs*cash wdl", "cash withdrawal",
     "atm cash", "atm txn", "cash wdl", "cash wthdl"

   For withdrawals:
     - group_merchant = null, clean_merchant = false
     - merchant = "Withdrawal ({cardNumber})"  if a cardNumber group is captured
     - merchant = "Withdrawal ({account})"     if only an account group is captured
     - merchant = "Withdrawal"                 if neither is captured
   NEVER create a (?<merchant>...) capture group for a withdrawal pattern.

   The word "debited" alone is NOT a withdrawal signal — use "Bank Debit" for generic account debits
   with no ATM/cash indicator.

   ONLY TWO cases get a {placeholder} appended (do NOT add one to any other label):
     1. ATM / cash withdrawals → merchant="Withdrawal ({cardNumber})" if a card group is captured,
        "Withdrawal ({account})" if only an account group is captured, bare "Withdrawal" if neither.
        Triggers: "withdrawn", "withdrawal", "atm wdl", "nfs*cash wdl", "cash withdrawal",
        "atm cash", "atm txn", "cash wdl", "cash wthdl".
     2. Account-to-account transfers with a captured beneficiary account →
        "UPI Transfer ({beneficiaryAccount})", "NEFT Transfer ({beneficiaryAccount})",
        "IMPS Transfer ({beneficiaryAccount})", "RTGS Transfer ({beneficiaryAccount})".
        (See the ACCOUNT-TO-ACCOUNT TRANSFERS section.)

   For every other generic label, the user's own account/card number adds no value —
   leave the label bare. e.g. "Salary", NOT "Salary ({account})"; "POS Transaction",
   NOT "POS Transaction ({cardNumber})".

   PLACEHOLDER RULES (apply to the two allowed cases above):
     - The placeholder name MUST be a named group that EXISTS in your regex AND is
       declared in the matching group_* field (e.g. {account} ⇒ group_account="account").
     - Use exactly {account} (ATM) or {beneficiaryAccount} (A2A transfer). No others.
     - NEVER put {reference}, {amount}, {balance}, {date}, or {bankName} in merchant.
     - {cardNumber} is allowed ONLY in "Withdrawal ({cardNumber})" when no account group exists.
     - Use at most ONE placeholder per merchant string.

c) NULL — only if the message carries zero payee information and no static label fits.

FORBIDDEN merchant strings (never use these unless the explicit trigger is present):
  - "ATM Withdrawal" — always use bare "Withdrawal" or "Withdrawal ({account})". Never prefix with "ATM".
  - "Withdrawal" (bare, without trigger) — use "Bank Debit" for plain account debits with no ATM/cash indicator. A capture group for the ATM location ("at <place>") is NEVER a payee merchant.
  - "Transfer" (bare) — always qualify: "UPI Transfer", "NEFT Transfer", etc.
  - A bank name (e.g. "IOB Bank", "HDFC Bank") — the bank is never the payee merchant.

SIGNALS that <MERCHANT> was mis-tagged — these must NOT create a capture group; use a static label:
  - The input may contain a <HOTLINE> token. This is a pre-identified fraud-report shortcode.
    NEVER capture <HOTLINE> as a merchant group. Match it as \\S+ (non-capturing).
  - Follows "report to", "call", "sms", "dial", "block", "helpline", "not you?" → fraud hotline /
    helpline number. Match it as \\S+ (non-capturing). This is the most common false positive.
  - Appears after "sms block <NUM> to" or "sms block to" → it is a shortcode, NOT a payee. Use \\S+.
  - Follows "salary payment", "salary credit" → merchant="Salary"
  - Follows "towards neft/rtgs/imps" with no distinct payee word → "NEFT Transfer" (bare,
    unless a beneficiary account is captured → "NEFT Transfer ({beneficiaryAccount})")
  - ATM / cash withdrawal trigger present ("withdrawn", "withdrawal", "atm wdl", "nfs*cash wdl",
    "cash withdrawal", "atm cash", "atm txn", "cash wdl", "cash wthdl") →
    group_merchant=null, clean_merchant=false.
    merchant="Withdrawal ({cardNumber})" if card captured, "Withdrawal ({account})" if only account
    captured, bare "Withdrawal" if neither. The "at <location>" fragment after the amount is the
    ATM branch/location — match it non-capturing, NEVER as (?<merchant>...).
  - POS / card txn with NO identifiable merchant name → merchant="POS Transaction" (bare)
  - Is a bank name, city name, or generic banking term → not a payee; use a bare static label
  - Follows "info:" or "info-" in bill/recharge messages (e.g. "bil*rchg*", "bil*nucl*") →
    the info code is a bill category code, not varying. Use a static label like "Bill Payment",
    "Recharge", "Electricity Bill", "Insurance Premium" based on the code.

POS / UPI MERCHANT CODES — ALWAYS CAPTURE DYNAMICALLY, NEVER HARDCODE:
  When the skeleton contains a merchant embedded inside a structured code or prefix, the
  code format tells you the merchant FIELD varies per transaction. You MUST capture it
  dynamically with (?<merchant>...) — never hardcode the name as a static string, even if
  you recognise it.

  RULE: if the merchant name appears INSIDE a coded format below, use a capture group:

  1. "min*<merchant_code>"  e.g. "min*www amazo", "min*iit guwah", "min*netflix"
     → match as: min\*(?<merchant>[A-Za-z0-9 &.*_-]+?)(?:[.\s]|$)
     → group_merchant="merchant", clean_merchant=true

  2. "upi-<NUM>-<merchant_code>"  e.g. "upi-9876-zepto", "upi-123-swiggy"
     → match as: upi\-\d+\-(?<merchant>[A-Za-z0-9&._*-]+?)(?:[.\s]|$)
     → group_merchant="merchant", clean_merchant=true

  3. "<location>-pos-<merchant_code>"  e.g. "vasundhara enclave-pos-google c", "pos-bms"
     → match as: (?:[A-Za-z0-9 _]+\-)?pos\-(?<merchant>[A-Za-z0-9 &._*-]+?)(?:\s+on\b|$)
     → group_merchant="merchant", clean_merchant=true

  4. "nfs*<merchant_code>"  e.g. "nfs*cash wdl*swiggy"  (the part after the LAST * is merchant)
     → if the code contains "cash wdl" it IS an ATM withdrawal — use "Withdrawal ({account})"/"Withdrawal ({cardNumber})" or bare "Withdrawal"
     → otherwise capture the last segment: \*(?<merchant>[A-Za-z0-9 &._*-]+?)(?:[.\s]|$)

  The recognisable-name test ("I know this is Amazon/Netflix/Zepto") is IRRELEVANT for
  coded formats — the field varies even when you recognise one value.

ACCOUNT-TO-ACCOUNT TRANSFERS (UPI/NEFT/IMPS/RTGS):
  READ THE SURROUNDING WORDS to decide which <ACCTNUM> belongs to the user and which is the beneficiary.
  Do NOT assume the FIRST <ACCTNUM> is always the user's account — it depends on phrasing:
    - "credited to beneficiary ac no xxxx<ACCTNUM>" → that <ACCTNUM> IS the beneficiary, NOT the user
    - "your a/c xx<ACCTNUM> debited ... to a/c xx<ACCTNUM>" → first = user, second = beneficiary
    - "neft with ref.no: ... credited to beneficiary ac no xxxx<ACCTNUM>" → only one <ACCTNUM> and it
      is the beneficiary; set group_account=null, capture as (?<beneficiaryAccount>\w+)
    - "debited from a/c xx<ACCTNUM> ... credited to a/c xx<ACCTNUM>" → first = user, second = beneficiary
  When the skeleton says "beneficiary" explicitly → always use (?<beneficiaryAccount>\w+), group_account=null.
  When TWO <ACCTNUM> tokens appear in a debit skeleton:
    - First <ACCTNUM>  = the user's own source account → (?<account>\w+), group_account="account"
    - Second <ACCTNUM> = the beneficiary/destination account → (?<beneficiaryAccount>\w+)
      (different group name — do NOT reuse "account")
  Build the merchant template using the transfer type and beneficiary account number:
    merchant="UPI Transfer ({beneficiaryAccount})"   — UPI
    merchant="NEFT Transfer ({beneficiaryAccount})"  — NEFT
    merchant="IMPS Transfer ({beneficiaryAccount})"  — IMPS
    merchant="RTGS Transfer ({beneficiaryAccount})"  — RTGS
    merchant="UPI Transfer" / "NEFT Transfer" / "IMPS Transfer" / "RTGS Transfer"  — no account available
  Do NOT include reference/UTR numbers in the merchant string. Never use {reference} in merchant.
  Still capture the UPI/UTR reference as (?<reference>\d+), set group_reference="reference" — but keep it out of merchant.
  group_merchant must be null for all template cases; set merchant= the template string.

BANK NAME AS LITERAL AT END OF MESSAGE:
  Many SMS messages end with "- HDFC Bank", "- IDBI Bank", "- Canara Bank" as a signature.
  Do NOT hardcode the bank name literally. Instead capture it:
    (?<bankName>[A-Za-z]{2,20}(?:\s+[A-Za-z]{2,10})?)\s+[Bb]ank
  and set group_bank_name="bankName".

UPI HANDLE: if the real payee follows @ or @upi_ in the skeleton (even if tagged as <VPA>),
capture only the username part (text before @) — not the bank handle suffix:
  (?<merchant>[A-Za-z0-9._\\-]+)(?:@[A-Za-z0-9]+)
Set clean_merchant=true (the raw VPA username needs title-casing and suffix stripping).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ACCOUNT LABEL TYPE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Set account_label_type only when <BANK> appears alongside an account/card token:
  "Card"        — generic card (unknown type)
  "Debit Card"  — skeleton context says debit
  "Credit Card" — skeleton context says credit
  "Acct"        — bank account / savings / current
  "SuperCard"   — co-branded card
Set to null if no <BANK> token is present.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OTHER FIELDS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
transaction_type: "debit" (debited/spent/paid/withdrawn/sent), "credit" (credited/received/deposited), "detect" if ambiguous.
default_currency: "INR" unless another currency is evident.
version: 1.
sender_id: set from the input — do NOT invent one.

DO NOT include: id, created_at, updated_at, synced_at, is_active.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OUTPUT FORMAT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
If the skeleton FAILS the STEP 0 validity gate (not a completed transaction),
return ONLY:
  {"skip": true, "skip_reason": "<short reason, e.g. 'upi-mandate request - not completed'>"}

Otherwise, return ONLY a single valid JSON object with these keys:
  regex, regex_options, transaction_type, priority,
  group_amount, group_balance, group_credit_limit, group_currency, group_date, group_account,
  group_merchant, group_reference, group_card_number, group_bank_name,
  account_label_type, default_currency, clean_merchant, merchant, sender_id, version.

Set group_balance to the capture group name (e.g. "balance") if an account-balance token is
captured, otherwise null.
Set group_credit_limit to the capture group name (e.g. "creditLimit") if an available-credit-limit
token is captured, otherwise null.

When a SENDER CONFLICT is indicated in the input, also include:
  sender_id_suggestion: "generic" if one regex works for all banks,
                        "split" if banks differ enough to warrant separate patterns.

No markdown fences. No explanation. Just the JSON object."""


def resolve_sender_id(variants: list[dict]) -> tuple[str | None, bool, dict[str, list[str]]]:
    """
    Returns (resolved_sender_id, has_conflict, bank_code_map).

    TRAI sender codes come in two formats:
      - Hyphenated:    PREFIX-BANKCODE  (e.g. "AX-HDFCBK", "VK-AUBANK")
      - Non-hyphenated: raw codes with optional 2-letter prefix (e.g. "VDICICIB", "ICICIB", "IOBCHN", "IOBCHN-S")

    Normalisation — extract the canonical bank root:
      1. If hyphenated (PREFIX-BANKCODE): bank root = BANKCODE, strip any trailing "-S"/"-T" suffix.
      2. If not hyphenated: strip a leading 2-letter alpha prefix if the remainder is ≥4 chars
         (e.g. "VD" + "ICICIB" → "ICICIB"; "ICICIB" → "ICICIB"), then strip trailing "-S"/"-T".

    - Single bank root across all variants → (representative sender_id, False, {root: [sids]})
    - Multiple bank roots                  → (None, True, {root1: [sids], root2: [sids], ...})
    - No sender_id in any variant          → (None, False, {})
    """
    sender_ids = [r["sender_id"] for r in variants if r.get("sender_id")]
    if not sender_ids:
        return None, False, {}

    # Aliases: non-hyphenated codes that refer to the same bank.
    # Key = raw code, value = canonical root to use for grouping.
    _BANK_ALIASES: dict[str, str] = {
        "BIBK": "IDBIBK",   # IDBI Bank — BIBK is a truncated alternate code
    }

    def bank_root(sid: str) -> str:
        # Strip hyphenated suffix variants like "-S", "-T" (e.g. "IOBCHN-S" → "IOBCHN")
        sid = re.sub(r"-[A-Z]$", "", sid)
        if "-" in sid:
            # Hyphenated format: PREFIX-BANKCODE → take BANKCODE
            root = sid.split("-", 1)[1]
        else:
            # Non-hyphenated: strip leading 2-letter alpha TRAI prefix if remainder ≥4 chars
            # e.g. CPIDBIBK → IDBIBK, VKIDBIBK → IDBIBK, JDICICIB → ICICIB
            m = re.match(r"^[A-Z]{2}([A-Z]{4,})$", sid)
            root = m.group(1) if m else sid
        # Apply alias (e.g. BIBK → IDBIBK)
        return _BANK_ALIASES.get(root.upper(), root)

    # Group sender IDs by normalised bank root
    bank_code_map: dict[str, list[str]] = {}
    for sid in sender_ids:
        root = bank_root(sid)
        bank_code_map.setdefault(root, [])
        if sid not in bank_code_map[root]:
            bank_code_map[root].append(sid)

    if len(bank_code_map) == 1:
        # Single bank — pick representative sender_id from highest-hit variant
        best = max(
            (r for r in variants if r.get("sender_id")),
            key=lambda r: r["hit_count"],
        )
        return best["sender_id"], False, bank_code_map
    else:
        # Multiple banks — conflict; let Claude suggest
        return None, True, bank_code_map


def _find_varying_tokens(cluster_key: str, variants: list[dict]) -> list[str]:
    """
    Returns literal word-runs from cluster_key that are absent from ≥20% of
    variants — i.e. merchant/payee names the anonymizer missed.

    Strategy: split on placeholder boundaries, then within each literal segment
    extract maximal runs of non-stopword words and check if each run is present
    in all variants. Runs absent from ≥20% of variants are "varying".
    """
    if len(variants) <= 1:
        return []

    structural = STOPWORDS | BANK_NAMES | {
        "dear", "customer", "team", "first", "available",
        "new", "closing", "ledger", "clr", "avbl", "via",
        "rrn", "upi", "imps", "neft", "rtgs", "ref", "utr",
        "bill", "paid", "pay", "payment", "towards",
    }

    key_lower = cluster_key.lower()
    # Split on placeholder tokens — each segment is a run of literal text
    segments = re.split(r"<[a-z]+>", key_lower, flags=re.IGNORECASE)

    varying = []
    for seg in segments:
        # Tokenise the segment
        raw_words = re.findall(r"[a-z][a-z0-9&'._-]*", seg)
        if not raw_words:
            continue

        # Extract maximal runs of non-structural words (potential merchant names)
        run: list[str] = []
        for w in raw_words:
            if w not in structural and len(w) >= 3:
                run.append(w)
            else:
                if run:
                    phrase = " ".join(run)
                    hit = sum(1 for v in variants if phrase in v["skeleton_text"].lower())
                    if hit < len(variants) * 0.80:
                        varying.append(phrase)
                    run = []
        if run:
            phrase = " ".join(run)
            hit = sum(1 for v in variants if phrase in v["skeleton_text"].lower())
            if hit < len(variants) * 0.80:
                varying.append(phrase)

    return varying


def _cluster_block(
    idx: int,
    cluster_key: str,
    variants: list[dict],
    resolved_sender_id: str | None,
    has_conflict: bool,
    bank_code_map: dict[str, list[str]],
) -> str:
    """Build one cluster's section inside a batch prompt."""
    top = variants[:6]
    variant_lines = "\n".join(
        f"    [{r['hit_count']} hits] [{r.get('sender_id', 'unknown')}] {r['skeleton_text']}"
        for r in top
    )
    if not bank_code_map:
        sender_note = "Sender ID: unknown"
    elif has_conflict:
        bank_breakdown = ", ".join(
            f"{bc} ({', '.join(sids)})" for bc, sids in bank_code_map.items()
        )
        sender_note = (
            f"SENDER CONFLICT — spans banks: {bank_breakdown}. "
            f"Add sender_id_suggestion: \"generic\" or \"split\". Set sender_id=null."
        )
    else:
        raw_sids = [sid for sids in bank_code_map.values() for sid in sids]
        sender_note = f"Sender ID: {resolved_sender_id} (raw: {', '.join(raw_sids)})"

    varying = _find_varying_tokens(cluster_key, variants)
    varying_note = (
        f"VARYING WORDS (differ across variants — use (?<merchant>...) NOT a literal): {varying}"
        if varying else ""
    )

    fraud_note = (
        "FRAUD TAIL: this skeleton ends with a fraud-report contact token — do NOT capture it as merchant."
        if "<HOTLINE>" in _strip_fraud_tail(cluster_key) or
           re.search(r"(?:sms\s+block|call|dial)\s+(?:<NUM>|<MERCHANT>)\s*$", cluster_key, re.IGNORECASE)
        else ""
    )

    notes = "\n".join(n for n in [varying_note, fraud_note] if n)

    return (
        f"### CLUSTER {idx}\n"
        f"{sender_note}\n"
        f"Variants: {len(variants)}\n"
        f"Key: {cluster_key}\n"
        + (f"Notes: {notes}\n" if notes else "")
        + f"Examples:\n{variant_lines}"
    )


def build_batch_prompt(clusters: list[tuple]) -> str:
    """
    clusters: list of (idx, cluster_key, variants, resolved_sender_id, has_conflict, bank_code_map)
    Returns a single prompt asking Claude to return a JSON array with one object per cluster.
    """
    blocks = [_cluster_block(*c) for c in clusters]
    joined = "\n\n".join(blocks)
    n = len(clusters)
    return (
        f"Process the following {n} SMS clusters. "
        f"Return a JSON ARRAY with exactly {n} objects, one per cluster, in the same order.\n"
        f"Each object must follow the sms_patterns schema from the system prompt.\n\n"
        f"{joined}\n\n"
        f"Return ONLY a valid JSON array. No markdown, no explanation."
    )


def call_claude_batch(
    client: anthropic.Anthropic,
    clusters: list[tuple],
    model: str = DEFAULT_MODEL,
) -> list[dict | None]:
    """
    Send up to BATCH_SIZE clusters in one API call.
    Returns a list of parsed dicts (or None for failed entries), same length as clusters.
    """
    prompt = build_batch_prompt(clusters)
    try:
        response = client.messages.create(
            model=model,
            max_tokens=1024 * len(clusters),
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )
        raw = response.content[0].text.strip()
        raw = re.sub(r"^```(?:json)?\s*", "", raw)
        raw = re.sub(r"\s*```$", "", raw)
        parsed = json.loads(raw)
        if not isinstance(parsed, list):
            print(f"  [WARN] Expected JSON array, got {type(parsed).__name__}")
            return [None] * len(clusters)
        # Pad or trim to match cluster count
        while len(parsed) < len(clusters):
            parsed.append(None)
        return parsed[:len(clusters)]
    except json.JSONDecodeError as e:
        print(f"  [WARN] JSON parse error in batch: {e}")
        return [None] * len(clusters)
    except Exception as e:
        print(f"  [WARN] Claude API error: {e}")
        return [None] * len(clusters)


# ---------------------------------------------------------------------------
# Step 4 — Validate regex against cluster members
# ---------------------------------------------------------------------------

# Substitute each skeleton placeholder with a concrete example string.
# These are used to synthesise a fake-but-matchable SMS for regex validation.
_PLACEHOLDER_EXAMPLES = {
    "<AMOUNT>":   "1,234.56",
    "<BANK>":     "HDFC",
    "<DATE>":     "05-Jun-2025",
    "<TIME>":     "10:30:00",
    "<ACCTNUM>":  "1234",   # digits only — skeleton already carries any "xx"/"xxxxxx" mask prefix
    "<CARDNUM>":  "5678",   # digits only — same reason
    "<REF>":      "412345678901",
    "<VPA>":      "user@upi",
    "<NUM>":      "123456",
    "<MERCHANT>": "Zomato",
    "<M>":        "Merchant",
}

def _skeleton_to_synthetic_sms(skeleton: str) -> str:
    """Replace placeholders with concrete example values to produce a fake matchable SMS."""
    s = skeleton
    for placeholder, example in _PLACEHOLDER_EXAMPLES.items():
        s = s.replace(placeholder, example)
    return s


def validate_pattern(row: dict, variants: list[dict]) -> tuple[bool, float]:
    """
    Returns (passed, match_rate).
    A pattern passes if ≥90% of cluster members match the regex.

    Claude's regex targets raw SMS text, but we only have anonymised skeletons.
    We synthesise a fake raw SMS from each skeleton by substituting placeholders
    with concrete example strings, then test Claude's regex against those.
    """
    try:
        flags = 0
        for opt in row.get("regex_options", []):
            if opt == "IGNORE_CASE":
                flags |= re.IGNORECASE
            elif opt == "MULTILINE":
                flags |= re.MULTILINE
            elif opt == "DOT_MATCHES_ALL":
                flags |= re.DOTALL
        # Convert Kotlin named groups (?<name>...) → Python (?P<name>...) for re module
        python_regex = re.sub(r"\(\?<([A-Za-z][A-Za-z0-9]*)>", r"(?P<\1>", row["regex"])
        pattern = re.compile(python_regex, flags)
    except re.error as e:
        print(f"    [FAIL] Invalid regex: {e}")
        return False, 0.0

    matched = sum(
        1 for v in variants
        if pattern.search(_skeleton_to_synthetic_sms(v["skeleton_text"]))
    )
    rate = matched / len(variants)
    return rate >= 0.90, rate


# ---------------------------------------------------------------------------
# Step 5 — Sanitise and insert into Supabase
# ---------------------------------------------------------------------------

SUPABASE_COLUMNS = [
    # "id" intentionally omitted — Supabase generates it; Claude must not provide it
    "regex", "regex_options", "transaction_type", "priority",
    "group_amount", "group_balance", "group_credit_limit", "group_currency", "group_date",
    "group_account", "group_merchant", "group_reference", "group_card_number", "group_bank_name",
    "account_label_type", "default_currency", "clean_merchant", "merchant",
    "sample_sms", "sender_id", "version",
]


def sanitise_row(row: dict, cluster_key: str) -> dict:
    """Keep only known columns, fill defaults, attach sample_sms."""
    out = {col: row.get(col) for col in SUPABASE_COLUMNS}
    out["sample_sms"] = cluster_key  # canonical skeleton for reference
    out.setdefault("default_currency", "INR")
    out.setdefault("priority", 200)
    out.setdefault("version", 1)
    out.setdefault("clean_merchant", False)
    # regex_options must be a list for Supabase (text[] column)
    if isinstance(out.get("regex_options"), str):
        out["regex_options"] = [o.strip() for o in out["regex_options"].split(",") if o.strip()]
    return out


def upsert_patterns(supabase_client, rows: list[dict]):
    batch_size = 50
    total = 0
    for i in range(0, len(rows), batch_size):
        batch = rows[i: i + batch_size]
        supabase_client.table("sms_patterns").upsert(batch).execute()
        total += len(batch)
        print(f"  Upserted {total}/{len(rows)} rows...")
    print(f"Done. {total} patterns inserted/updated.")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

BATCH_SIZE = 10  # clusters per API call


def main():
    parser = argparse.ArgumentParser(description="Generate sms_patterns from unknown_patterns via Claude")
    parser.add_argument(
        "--model", default=DEFAULT_MODEL,
        help=f"Anthropic model to use (default: {DEFAULT_MODEL}). "
             "Use 'claude-sonnet-4-6' for better quality or 'claude-opus-4-8' for best quality.",
    )
    parser.add_argument(
        "--limit", type=int, default=None,
        help="Limit number of rows fetched from Supabase (for testing, e.g. --limit 20).",
    )
    parser.add_argument(
        "--batch-size", type=int, default=BATCH_SIZE,
        help=f"Clusters per API call (default: {BATCH_SIZE}). Higher = fewer calls but larger prompts.",
    )
    parser.add_argument(
        "--dump-clusters", action="store_true",
        help="Write clusters to scripts/clusters_dump.json and exit — no LLM calls.",
    )
    parser.add_argument(
        "--eps", type=float, default=0.15,
        help="DBSCAN epsilon: max normalised edit distance to be in the same cluster (default: 0.15).",
    )
    parser.add_argument(
        "--upload", metavar="FILE",
        help=(
            "Upload patterns from a reviewed JSON file directly to sms_patterns, then exit. "
            "Accepts patterns_ready.json (flat list) or patterns_to_review.json / any file whose "
            "items have a nested 'pattern' key. Example: --upload scripts/patterns_to_review.json"
        ),
    )
    parser.add_argument(
        "--reprocess", metavar="FILE",
        help=(
            "Re-send entries from patterns_skipped_fake.json through Claude, bypassing the "
            "classify_skeleton() gate. Use --reprocess-indices to pick specific entries, or "
            "omit to reprocess all. Example: --reprocess scripts/patterns_skipped_fake.json"
        ),
    )
    parser.add_argument(
        "--reprocess-indices", metavar="N[,N...]", default=None,
        help="Comma-separated indices from the skipped file to reprocess. E.g. --reprocess-indices 0,2",
    )
    args = parser.parse_args()

    if args.upload:
        file_path = Path(args.upload)
        if not file_path.exists():
            print(f"File not found: {file_path}")
            return
        with open(file_path) as f:
            data = json.load(f)
        if not isinstance(data, list) or not data:
            print("File must contain a non-empty JSON array.")
            return
        # Unwrap nested {pattern: {...}} shape (patterns_to_review.json)
        rows = [item["pattern"] if isinstance(item, dict) and "pattern" in item else item for item in data]
        print(f"Loaded {len(rows)} patterns from {file_path}")
        print(f"First regex preview: {rows[0].get('regex', '')[:80]}")
        ans = input(f"\nInsert {len(rows)} patterns into sms_patterns? [y/N] ").strip().lower()
        if ans == "y":
            supabase_client = create_client(SUPABASE_URL, SUPABASE_KEY)
            upsert_patterns(supabase_client, rows)
        else:
            print("Aborted.")
        return

    if args.reprocess:
        file_path = Path(args.reprocess)
        if not file_path.exists():
            print(f"File not found: {file_path}")
            return
        with open(file_path) as f:
            skipped = json.load(f)
        if not isinstance(skipped, list) or not skipped:
            print("File must contain a non-empty JSON array.")
            return

        # Print menu so user can pick which entries to reprocess
        print(f"\nEntries in {file_path.name}:")
        for i, s in enumerate(skipped):
            print(f"  [{i}] hits={s['total_hits']:>4}  reason={s['reason'][:50]}")
            print(f"       {s['cluster_key'][:90]}")

        if args.reprocess_indices is not None:
            try:
                indices = [int(x.strip()) for x in args.reprocess_indices.split(",")]
            except ValueError:
                print("--reprocess-indices must be comma-separated integers")
                return
        else:
            raw = input("\nIndices to reprocess (comma-separated, or Enter for all): ").strip()
            if raw:
                indices = [int(x.strip()) for x in raw.split(",")]
            else:
                indices = list(range(len(skipped)))

        selected = [skipped[i] for i in indices]
        print(f"\nReprocessing {len(selected)} entries (classify_skeleton gate bypassed)...")

        claude_client = anthropic.Anthropic(api_key=ANTHROPIC_KEY)
        supabase_client = create_client(SUPABASE_URL, SUPABASE_KEY)

        # Reconstruct cluster tuples expected by call_claude_batch
        pending = []
        for entry in selected:
            cluster_key = entry["cluster_key"]
            # Rebuild minimal variant dicts from sample_variants (no hit_count/sender_id available)
            variants = [
                {"skeleton_text": s, "sender_id": None, "hit_count": 1, "pattern_hash": None}
                for s in entry.get("sample_variants", [cluster_key])
            ]
            resolved_sender_id, has_conflict, bank_code_map = resolve_sender_id(variants)
            pending.append((0, cluster_key, variants, resolved_sender_id, has_conflict, bank_code_map))

        results = call_claude_batch(claude_client, pending, args.model)

        reprocess_ready = []
        reprocess_review = []
        for (_, cluster_key, variants, resolved_sender_id, has_conflict, bank_code_map), pattern_row in zip(pending, results):
            print(f"\n  {cluster_key[:80]}")
            if pattern_row is None:
                print("    [ERROR] Claude returned null")
                continue
            if isinstance(pattern_row, dict) and pattern_row.get("skip"):
                print(f"    [SKIP] {pattern_row.get('skip_reason', '')[:80]}")
                continue
            pattern_row.pop("sender_id_suggestion", None)
            pattern_row["sender_id"] = resolved_sender_id
            passed, rate = validate_pattern(pattern_row, variants)
            print(f"    [{'PASS' if passed else 'FAIL'}] match_rate={rate:.0%}")
            sanitised = sanitise_row(pattern_row, cluster_key)
            if passed:
                reprocess_ready.append(sanitised)
            else:
                reprocess_review.append({"match_rate": round(rate, 3), "pattern": sanitised,
                                         "cluster_key": cluster_key, "sample_variants": [v["skeleton_text"] for v in variants]})

        scripts_dir = Path(__file__).parent
        with open(scripts_dir / "reprocess_ready.json", "w") as f:
            json.dump(reprocess_ready, f, indent=2, ensure_ascii=False)
        with open(scripts_dir / "reprocess_to_review.json", "w") as f:
            json.dump(reprocess_review, f, indent=2, ensure_ascii=False)

        print(f"\nReady: {len(reprocess_ready)}  Needs review: {len(reprocess_review)}")
        print(f"Saved to scripts/reprocess_ready.json and scripts/reprocess_to_review.json")

        if reprocess_ready:
            ans = input(f"\nInsert {len(reprocess_ready)} ready patterns into sms_patterns? [y/N] ").strip().lower()
            if ans == "y":
                upsert_patterns(supabase_client, reprocess_ready)
            else:
                print("Skipped. Use --upload scripts/reprocess_ready.json when ready.")
        return

    print(f"Using model: {args.model}  batch_size: {args.batch_size}")
    supabase_client = create_client(SUPABASE_URL, SUPABASE_KEY)
    claude_client = anthropic.Anthropic(api_key=ANTHROPIC_KEY)

    # 1. Fetch
    rows = fetch_unknown_patterns(supabase_client, limit=args.limit)
    if not rows:
        print("No unknown patterns found. Exiting.")
        return

    # 2. Cluster (sorted by total hits descending)
    clusters = cluster_skeletons(rows, eps=args.eps)

    if args.dump_clusters:
        dump = []
        for cluster_key, variants in clusters.items():
            dump.append({
                "cluster_key": cluster_key,
                "total_hits": sum(v["hit_count"] for v in variants),
                "variant_count": len(variants),
                "skeletons": [
                    {"skeleton_text": v["skeleton_text"], "sender_id": v.get("sender_id"), "hit_count": v["hit_count"]}
                    for v in variants
                ],
            })
        out_path = Path(__file__).parent / "clusters_dump.json"
        with open(out_path, "w") as f:
            json.dump(dump, f, indent=2, ensure_ascii=False)
        print(f"Wrote {len(dump)} clusters to {out_path}")
        return

    ready: list[dict] = []
    review: list[dict] = []
    errors: list[dict] = []
    skipped: list[dict] = []
    split_suggestions: list[dict] = []
    processed_hashes: list[str] = []  # pattern_hashes to delete after the run

    cluster_items = list(clusters.items())
    total = len(cluster_items)

    # Pre-flight: classify and resolve sender_id for all clusters
    pending: list[tuple] = []   # (idx, cluster_key, variants, resolved_sender_id, has_conflict, bank_code_map)
    for idx, (cluster_key, variants) in enumerate(cluster_items, 1):
        total_hits = sum(v["hit_count"] for v in variants)
        is_genuine, reason = classify_skeleton(cluster_key)
        if not is_genuine:
            print(f"[{idx}/{total}] SKIP — {reason[:80]}")
            skipped.append({
                "cluster_key": cluster_key,
                "reason": reason,
                "total_hits": total_hits,
                "variant_count": len(variants),
                "sample_variants": [v["skeleton_text"] for v in variants[:3]],
            })
            processed_hashes.extend(v["pattern_hash"] for v in variants if v.get("pattern_hash"))
            continue
        resolved_sender_id, has_conflict, bank_code_map = resolve_sender_id(variants)
        pending.append((idx, cluster_key, variants, resolved_sender_id, has_conflict, bank_code_map))

    print(f"\n{len(skipped)} skipped (fake/promo), {len(pending)} genuine clusters → sending to Claude in batches of {args.batch_size}")

    # Process in batches
    for batch_start in range(0, len(pending), args.batch_size):
        batch = pending[batch_start: batch_start + args.batch_size]
        batch_indices = [c[0] for c in batch]
        print(f"\n--- Batch clusters {batch_indices[0]}–{batch_indices[-1]} ({len(batch)} clusters) ---")

        # Build tuples for call_claude_batch: (idx, key, variants, sender_id, conflict, bank_map)
        results = call_claude_batch(claude_client, batch, args.model)

        for (idx, cluster_key, variants, resolved_sender_id, has_conflict, bank_code_map), pattern_row in zip(batch, results):
            total_hits = sum(v["hit_count"] for v in variants)
            print(f"  [{idx}/{total}] {total_hits} hits | {len(variants)} variants | {cluster_key[:80]}")

            if pattern_row is None:
                print(f"    [ERROR] Claude returned null for this cluster")
                errors.append({"cluster_key": cluster_key, "variants": len(variants)})
                continue  # don't delete — will retry next run

            # VALIDITY GATE — Claude judged this not a completed transaction
            if isinstance(pattern_row, dict) and pattern_row.get("skip"):
                reason = pattern_row.get("skip_reason", "llm marked not a valid transaction")
                print(f"    [SKIP] {reason[:80]}")
                skipped.append({
                    "cluster_key": cluster_key,
                    "reason": f"llm skip: {reason}",
                    "total_hits": total_hits,
                    "variant_count": len(variants),
                    "sample_variants": [v["skeleton_text"] for v in variants[:3]],
                })
                processed_hashes.extend(v["pattern_hash"] for v in variants if v.get("pattern_hash"))
                continue

            # Handle sender_id_suggestion for conflicted clusters
            claude_suggestion = pattern_row.pop("sender_id_suggestion", None)
            if has_conflict:
                banks = ", ".join(bank_code_map.keys())
                if claude_suggestion == "split":
                    print(f"    [SPLIT] Claude recommends per-bank patterns (banks: {banks})")
                    split_suggestions.append({
                        "cluster_key": cluster_key,
                        "bank_code_map": bank_code_map,
                        "total_hits": total_hits,
                        "variant_count": len(variants),
                        "sample_variants": [v["skeleton_text"] for v in variants[:5]],
                    })
                    processed_hashes.extend(v["pattern_hash"] for v in variants if v.get("pattern_hash"))
                    continue
                else:
                    print(f"    [GENERIC] treating as bank-agnostic (banks: {banks})")

            pattern_row["sender_id"] = resolved_sender_id

            # Validate
            passed, rate = validate_pattern(pattern_row, variants)
            status = "PASS" if passed else "FAIL"
            print(f"    [{status}] match_rate={rate:.0%}")

            sanitised = sanitise_row(pattern_row, cluster_key)
            if passed:
                ready.append(sanitised)
            else:
                review.append({
                    "match_rate": round(rate, 3),
                    "pattern": sanitised,
                    "cluster_key": cluster_key,
                    "sample_variants": [v["skeleton_text"] for v in variants[:3]],
                })
            # Mark as processed regardless of pass/fail — pattern is in output files
            processed_hashes.extend(v["pattern_hash"] for v in variants if v.get("pattern_hash"))

        # Brief pause between batches to stay within rate limits
        if batch_start + args.batch_size < len(pending):
            time.sleep(2)

    # 5. Write output files
    scripts_dir = Path(__file__).parent
    with open(scripts_dir / "patterns_ready.json", "w") as f:
        json.dump(ready, f, indent=2, ensure_ascii=False)
    with open(scripts_dir / "patterns_to_review.json", "w") as f:
        json.dump(review, f, indent=2, ensure_ascii=False)
    with open(scripts_dir / "patterns_errors.json", "w") as f:
        json.dump(errors, f, indent=2, ensure_ascii=False)
    with open(scripts_dir / "patterns_skipped_fake.json", "w") as f:
        json.dump(skipped, f, indent=2, ensure_ascii=False)
    with open(scripts_dir / "patterns_split_suggestions.json", "w") as f:
        json.dump(split_suggestions, f, indent=2, ensure_ascii=False)

    skipped_hits = sum(s["total_hits"] for s in skipped)

    print(f"\n{'=' * 60}")
    print(f"Ready to insert : {len(ready)}")
    print(f"Needs review    : {len(review)}")
    print(f"API errors      : {len(errors)}")
    print(f"Skipped (fake)  : {len(skipped)} clusters  ({skipped_hits} total hits)")
    print(f"Split suggested : {len(split_suggestions)} clusters  (need per-bank patterns)")
    print(f"{'=' * 60}")

    if split_suggestions:
        print(f"\n--- Split Suggestions (multi-bank clusters, write separate patterns per bank) ---")
        print(f"{'Hits':>6}  Banks / Cluster Key")
        print("-" * 80)
        for s in sorted(split_suggestions, key=lambda x: x["total_hits"], reverse=True):
            banks = ", ".join(s["bank_code_map"].keys())
            print(f"{s['total_hits']:>6}  [{banks}]  {s['cluster_key'][:70]}")
        print(f"\nFull list saved to: scripts/patterns_split_suggestions.json")

    if skipped:
        print(f"\n--- Misclassification Report (fake/promotional skeletons in unknown_patterns) ---")
        print(f"{'Hits':>6}  {'Variants':>8}  Reason / Cluster Key")
        print("-" * 80)
        for s in sorted(skipped, key=lambda x: x["total_hits"], reverse=True)[:30]:
            key_short = s["cluster_key"][:70]
            print(f"{s['total_hits']:>6}  {s['variant_count']:>8}  [{s['reason']}]  {key_short}")
        if len(skipped) > 30:
            print(f"  ... and {len(skipped) - 30} more. See scripts/patterns_skipped_fake.json")
        print(f"\nFull list saved to: scripts/patterns_skipped_fake.json")

    # 6. Upsert ready patterns
    if ready:
        ans = input(f"\nInsert {len(ready)} ready patterns into sms_patterns? [y/N] ").strip().lower()
        if ans == "y":
            upsert_patterns(supabase_client, ready)
        else:
            print("Skipped insert. Manually upsert scripts/patterns_ready.json when ready.")
    else:
        print("No patterns passed validation. Check scripts/patterns_to_review.json.")

    # 7. Clean up processed rows from unknown_patterns.
    # Errors are intentionally excluded — those clusters will be retried on the next run.
    if processed_hashes:
        print(f"\nCleaning up {len(processed_hashes)} processed rows from unknown_patterns...")
        stamp_processed(supabase_client, processed_hashes)


if __name__ == "__main__":
    main()
