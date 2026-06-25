#!/usr/bin/env python3
"""
verify_patterns.py
------------------
Validates every entry in patterns_ready.json by:
1. Compiling the regex (catches syntax errors).
2. Synthesising a fake SMS from sample_sms (replaces placeholders with concrete values).
3. Running the regex against the synthetic SMS.
4. Checking the merchant capture specifically — reports what was captured vs. expected.

Output: a colour-coded terminal report + patterns_verification.json with full results.
"""

import json
import re
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).parent

# ── Placeholder → concrete example substitution (same as generate_patterns.py) ──
PLACEHOLDER_EXAMPLES = {
    "<AMOUNT>":   "1234.56",
    "<BANK>":     "HDFC",
    "<DATE>":     "05-Jun-2025",
    "<TIME>":     "10:30:00",
    "<ACCTNUM>":  "1234",
    "<CARDNUM>":  "5678",
    "<REF>":      "412345678901",
    "<VPA>":      "user@upi",
    "<NUM>":      "123456",
    "<MERCHANT>": "Zomato",
    "<M>":        "Merchant",
}


def synthetic_sms(skeleton: str) -> str:
    s = skeleton
    for ph, val in PLACEHOLDER_EXAMPLES.items():
        s = s.replace(ph, val)
    # Post-process: collapse any run of 6+ decimal digits (artefact of <NUM>.<NUM> expansion)
    # e.g. "123456.123456" → "123456.56" so balance regexes with \.\d{1,2} can still match.
    s = re.sub(r"(\d+)\.(\d{3,})", lambda m: f"{m.group(1)}.{m.group(2)[:2]}", s)
    return s


def kotlin_to_python_regex(pattern: str) -> str:
    """Convert Kotlin (?<name>…) named groups to Python (?P<name>…)."""
    return re.sub(r"\(\?<([A-Za-z][A-Za-z0-9]*)>", r"(?P<\1>", pattern)


# ── ANSI colours ──
GREEN  = "\033[32m"
RED    = "\033[31m"
YELLOW = "\033[33m"
CYAN   = "\033[36m"
RESET  = "\033[0m"
BOLD   = "\033[1m"


def classify_merchant_issue(entry: dict, match) -> str | None:
    """
    Returns a short issue description if the merchant capture looks wrong, else None.
    """
    gm = entry.get("group_merchant")
    static_merchant = entry.get("merchant")

    if not gm:
        # Static / template merchant — nothing to validate in the capture
        return None

    try:
        captured = match.group(gm) if match else None
    except (IndexError, re.error):
        return f"group_merchant='{gm}' declared but no such named group in regex"
    if captured is None:
        return "merchant group present but not captured"

    captured = captured.strip()
    skeleton = entry.get("sample_sms", "")

    # Bad: captured value is a bank-fraud hotline signal
    fraud_signals = ("report to", "call", "sms", "dial", "block", "helpline", "contact")
    if any(sig in captured.lower() for sig in fraud_signals):
        return f"captured fraud-contact token: '{captured}'"

    # Bad: captured value is a bank name
    bank_names = {"hdfc", "icici", "sbi", "axis", "kotak", "yes", "idfc", "pnb", "rbl",
                  "dbs", "hsbc", "citi", "boi", "indian", "canara", "union", "airtel",
                  "paytm", "iob", "idbi", "federal", "au", "bandhan"}
    if captured.lower() in bank_names:
        return f"captured bank name as merchant: '{captured}'"

    # Bad: captured value is a structural keyword
    structural = {"credited", "debited", "balance", "available", "bal", "avl", "upi",
                  "neft", "rtgs", "imps", "ref", "utr", "txn", "transaction",
                  "on", "to", "for", "at", "from", "via", "by"}
    if captured.lower() in structural:
        return f"captured structural keyword as merchant: '{captured}'"

    # Bad: captured value is suspiciously short
    if len(captured) < 2:
        return f"captured value too short: '{captured}'"

    # Bad: captured value ends with digits only (likely captured ref/amount)
    if re.fullmatch(r"[\d,. ]+", captured):
        return f"captured numeric-only value: '{captured}'"

    # Warn: captured value may be over-greedy (very long)
    if len(captured) > 50:
        return f"possibly over-greedy capture ({len(captured)} chars): '{captured[:60]}…'"

    # Check: if skeleton has a known concrete payee word that should be the merchant,
    # but what we captured looks like something else.
    # Exception: Paytm P2P transfers — paytm is the channel, not the payee.
    # Covers: "you have sent rs.X to NAME using paytm app"
    #         "received from NAME in your paytm payments bank"
    #         "credited back to NAME ... paytm"
    #         upi/cr/.../paytm/pytm/**<VPA>/payment (credit via UPI paytm handle)
    #         "you have paid rs.X ... to NAME ... m.paytm.me/care :ppbl" (paytm is sender)
    #         "sent to <VPA> from your paytm a/c ..." (paytm is the SOURCE account)
    p2p_paytm = re.search(
        r'(?:'
        r'\b(?:sent\s+(?:rs\.?)?<?[A-Z]+>?\s+to|received\s+from|credited\s+back\s+to)\b.{2,80}\b(?:using\s+paytm|paytm\s+(?:payments?\s+bank|app))\b'
        r'|paytm/pytm/\*\*<?VPA>?'
        r'|\bpaid\b.{2,80}\bto\b.{2,80}\bpaytm\.me\b'
        r'|paytm\.me/care'
        r'|\bsent\b.{2,80}\bto\b.{2,80}\bfrom\s+your\s+paytm\b'
        r'|\bfrom\s+your\s+paytm\s+(?:a/c|payments?\s+bank|wallet)\b'
        r')',
        skeleton,
        re.IGNORECASE,
    )
    known_payees = ["swiggy", "zomato", "amazon", "flipkart", "netflix", "paytm",
                    "uber", "ola", "phonepe", "gpay", "google pay", "myntra",
                    "bigbasket", "blinkit", "dunzo", "nykaa", "meesho"]
    for payee in known_payees:
        if payee == "paytm" and p2p_paytm:
            continue  # capturing person's name in P2P transfer is correct
        if payee in skeleton.lower() and payee not in captured.lower():
            return f"skeleton mentions '{payee}' but captured '{captured}' instead"

    return None  # looks fine


def verify_entry(entry: dict, idx: int) -> dict:
    skeleton = entry.get("sample_sms", "")
    sms = synthetic_sms(skeleton)
    regex_raw = entry.get("regex", "")
    options = entry.get("regex_options", [])
    gm = entry.get("group_merchant")
    static_merchant = entry.get("merchant")

    result = {
        "idx": idx,
        "sender_id": entry.get("sender_id"),
        "skeleton": skeleton,
        "synthetic_sms": sms,
        "regex": regex_raw,
        "group_merchant": gm,
        "static_merchant": static_merchant,
        "status": None,      # "pass" | "fail" | "warn" | "error"
        "match": None,
        "captured_merchant": None,
        "issue": None,
    }

    # 1. Compile
    flags = 0
    for opt in options:
        if opt == "IGNORE_CASE":   flags |= re.IGNORECASE
        elif opt == "MULTILINE":   flags |= re.MULTILINE
        elif opt == "DOT_MATCHES_ALL": flags |= re.DOTALL
    try:
        pattern = re.compile(kotlin_to_python_regex(regex_raw), flags)
    except re.error as e:
        result["status"] = "error"
        result["issue"] = f"regex compile error: {e}"
        return result

    # 2. Match
    m = pattern.search(sms)
    result["match"] = bool(m)

    if not m:
        result["status"] = "fail"
        result["issue"] = "regex did not match synthetic SMS"
        return result

    # 3. Check merchant capture
    if gm:
        try:
            result["captured_merchant"] = m.group(gm)
        except IndexError:
            result["captured_merchant"] = None

    merchant_issue = classify_merchant_issue(entry, m)
    if merchant_issue:
        result["status"] = "warn"
        result["issue"] = merchant_issue
    else:
        result["status"] = "pass"

    return result


def main():
    path = SCRIPTS_DIR / "patterns_ready.json"
    entries = json.loads(path.read_text())

    results = []
    counts = {"pass": 0, "fail": 0, "warn": 0, "error": 0}

    for idx, entry in enumerate(entries, 1):
        r = verify_entry(entry, idx)
        results.append(r)
        counts[r["status"]] += 1

    # ── Print report ──
    print(f"\n{BOLD}{'='*70}{RESET}")
    print(f"{BOLD}  patterns_ready.json verification  ({len(entries)} entries){RESET}")
    print(f"{BOLD}{'='*70}{RESET}\n")

    # Group by status for clarity
    for status, colour, label in [
        ("error", RED,    "COMPILE ERRORS"),
        ("fail",  RED,    "REGEX MISMATCHES"),
        ("warn",  YELLOW, "MERCHANT ISSUES"),
    ]:
        group = [r for r in results if r["status"] == status]
        if not group:
            continue
        print(f"{colour}{BOLD}── {label} ({len(group)}) {'─'*(50-len(label))}{RESET}")
        for r in group:
            print(f"\n  [{r['idx']:03d}] {CYAN}{r.get('sender_id','?')}{RESET}")
            print(f"  skeleton : {r['skeleton']}")
            print(f"  synthetic: {r['synthetic_sms']}")
            print(f"  issue    : {RED}{r['issue']}{RESET}" if status in ("error","fail")
                  else f"  issue    : {YELLOW}{r['issue']}{RESET}")
            if r.get("captured_merchant") is not None:
                print(f"  captured : '{r['captured_merchant']}'")
            if r.get("group_merchant"):
                print(f"  group    : {r['group_merchant']}")
        print()

    # Summary
    print(f"{BOLD}{'='*70}{RESET}")
    print(f"  {GREEN}PASS {counts['pass']:3d}{RESET}  |  "
          f"{YELLOW}WARN {counts['warn']:3d}{RESET}  |  "
          f"{RED}FAIL {counts['fail']:3d}  ERROR {counts['error']:3d}{RESET}")
    print(f"{BOLD}{'='*70}{RESET}\n")

    # Write full results
    out = SCRIPTS_DIR / "patterns_verification.json"
    out.write_text(json.dumps(results, indent=2, ensure_ascii=False))
    print(f"Full results written to: {out}\n")

    return 0 if (counts["fail"] + counts["error"]) == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
