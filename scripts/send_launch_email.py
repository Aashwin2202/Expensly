#!/usr/bin/env python3
"""
send_launch_email.py
--------------------
Fetches all emails from the Supabase `waitlist` table and sends each one
a launch announcement email via the Resend API.

Requirements:
    pip install supabase python-dotenv resend

Environment variables (put in .env or export):
    SUPABASE_URL
    SUPABASE_SERVICE_ROLE_KEY
    RESEND_API_KEY
    FROM_EMAIL        (e.g. "FinTrackAI <hello@yourdomain.com>")
    APP_LINK          (e.g. "https://yourapp.com")
"""

import os
import sys
import time
import argparse
from dotenv import load_dotenv
from supabase import create_client
import resend

load_dotenv()

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
RESEND_API_KEY = os.environ["RESEND_API_KEY"]
FROM_EMAIL = os.environ.get("FROM_EMAIL", "FinTrackAI <onboarding@resend.dev>")
APP_LINK = os.environ.get("APP_LINK", "https://yourapp.com")


def build_html(app_link: str) -> str:
    return f"""
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
</head>
<body style="margin:0;padding:0;background:#f9fafb;font-family:Arial,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f9fafb;padding:40px 0;">
    <tr>
      <td align="center">
        <table width="600" cellpadding="0" cellspacing="0"
               style="background:#ffffff;border-radius:12px;padding:40px;max-width:600px;">
          <tr>
            <td>
              <h1 style="color:#1a1a1a;font-size:28px;margin:0 0 8px;">
                FinTrackAI is live! 🎉
              </h1>
              <p style="color:#555;font-size:16px;line-height:1.6;margin:16px 0;">
                You signed up to be notified — and today's the day.
                FinTrackAI is officially launched and ready for you to explore.
              </p>
              <p style="color:#555;font-size:16px;line-height:1.6;margin:16px 0;">
                Track your finances smarter with AI-powered insights,
                automatic categorization, and real-time spending analysis.
              </p>
              <div style="text-align:center;margin:32px 0;">
                <a href="{app_link}"
                   style="background:#6366f1;color:#ffffff;text-decoration:none;
                          padding:14px 32px;border-radius:8px;font-size:16px;
                          font-weight:bold;display:inline-block;">
                  Get Started Now →
                </a>
              </div>
              <p style="color:#888;font-size:13px;line-height:1.6;margin:24px 0 0;">
                You're receiving this because you joined our waitlist.
                If you didn't sign up, you can safely ignore this email.
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>
"""


def fetch_emails(supabase) -> list[str]:
    response = supabase.table("waitlist").select("email").execute()
    return [row["email"] for row in response.data if row.get("email")]


def send_emails(emails: list[str], dry_run: bool = False):
    resend.api_key = RESEND_API_KEY

    total = len(emails)
    sent = 0
    failed = []

    print(f"{'[DRY RUN] ' if dry_run else ''}Sending to {total} recipients...\n")

    for i, email in enumerate(emails, 1):
        print(f"  [{i}/{total}] {email} ... ", end="", flush=True)

        if dry_run:
            print("skipped (dry run)")
            sent += 1
            continue

        try:
            resend.Emails.send({
                "from": FROM_EMAIL,
                "to": email,
                "subject": "FinTrackAI is live — you're in! 🚀",
                "html": build_html(APP_LINK),
            })
            print("sent")
            sent += 1
        except Exception as e:
            print(f"FAILED — {e}")
            failed.append((email, str(e)))

        # Resend free tier: 2 req/sec — stay safely under it
        time.sleep(0.6)

    print(f"\nDone. {sent}/{total} sent.", end="")
    if failed:
        print(f"  {len(failed)} failed:")
        for email, err in failed:
            print(f"    {email}: {err}")
    else:
        print()


def main():
    parser = argparse.ArgumentParser(description="Send launch emails to waitlist")
    parser.add_argument("--dry-run", action="store_true",
                        help="Fetch emails and print count but don't actually send")
    args = parser.parse_args()

    supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
    emails = fetch_emails(supabase)

    if not emails:
        print("No emails found in waitlist table. Exiting.")
        sys.exit(0)

    print(f"Found {len(emails)} emails in waitlist.")
    send_emails(emails, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
