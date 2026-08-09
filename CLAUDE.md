# Expensly

An Android app that automatically tracks expenses by reading bank and credit card transaction SMS messages — no manual entry required.

## Features

* Automatic transaction detection from bank/credit card SMS, with filtering for OTPs, promotions, failed/pending transactions, and fraud alerts
* Merchant recognition & categorization (food, groceries, shopping, travel, bills, health, entertainment, investment, etc.)
* Accounts & cards overview with per-account and per-card transaction history
* Budgets with category-level tracking
* Insights into spending patterns over time (local heuristics + optional LLM-generated insights via Groq)
* Reminders for upcoming/recurring payments
* Weekly summaries and an end-of-period "Wrapped" recap
* Multi-currency support with automatic conversion to INR

## Tech Stack

* Language: Kotlin
* UI: Jetpack Compose, Material 3
* DI: Hilt
* Navigation: Jetpack Navigation Compose
* Backend: Supabase (Postgres, migrations in [supabase/migrations](https://github.com/Aashwin2202/Expensly/blob/main/supabase/migrations))
* Local storage: Room (`data/local/db/`)
* Networking: Retrofit (Forex rates, Groq LLM API)
* Build: Gradle (Kotlin DSL), compileSdk 36, minSdk 26, targetSdk 36
* applicationId: `com.fintrackai`

## Project Structure

```
app/src/main/java/com/fintrackai/
├── analytics/       # Analytics event definitions/helper
├── data/
│   ├── local/db/     # Room entities & DAOs (transactions, accounts, budgets, sms patterns, etc.)
│   ├── local/preferences/
│   ├── remote/        # Forex API, Groq LLM API, remote merchant/pattern sync models
│   └── repository/    # TransactionRepository, pattern/category sync, wrong-SMS reporting
├── di/               # Hilt modules
├── domain/
│   ├── account/        # Account summary helpers
│   ├── category/        # Category catalog/constants
│   ├── format/           # Amount formatting helpers
│   ├── insights/          # Local + LLM-based spending insight generation
│   ├── merchant/           # Merchant mapping key helpers
│   ├── model/               # Core domain models (Transaction, SmsMessage, etc.)
│   ├── recurring/            # Recurring payment detection
│   ├── sms/                   # SMS filtering, parsing, extraction, categorization (see below)
│   ├── transactions/           # CSV import/export, transaction linking
│   └── wrapped/                 # "Wrapped" recap generation
├── navigation/       # Nav graph and screen routes
├── notification/     # Notification scheduling/handling, budget & daily-review workers
├── receiver/         # SmsTransactionReceiver (incoming SMS broadcast receiver)
├── ui/               # Compose screens, grouped by feature (accounts, auth, budget, home,
│                      #   insights, onboarding, settings, transactions, undetected, weeklysummary, wrapped)
└── work/              # Background workers (SMS processing, pattern reporting)
```

## Build, Run & Test

```bash
./gradlew assembleDebug          # build debug APK
./gradlew testDebugUnitTest      # unit tests (JVM, no emulator needed)
./gradlew connectedDebugAndroidTest  # instrumented tests (needs emulator/device)
./gradlew lintDebug               # lint
```

Existing unit tests live in `app/src/test/java/com/fintrackai/`:
`SmsProcessingTest.kt`, `SmsAnonymizerTest.kt`, `ForexServiceTest.kt`, `DailySummaryTest.kt`.

`sms-tester.html` at the repo root is a standalone tool for testing SMS parsing patterns outside the app — use it to validate new/changed regex patterns before wiring them into `BankPatternRegistry`.

Local config needed to build with full functionality (via `local.properties` / Gradle properties): `GROQ_API_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`. The app builds without them, but LLM-based insights and Supabase sync/reporting features will no-op.

Required runtime permissions: `READ_SMS`, `RECEIVE_SMS`, `POST_NOTIFICATIONS`, `INTERNET`.

## Domain Rules — SMS Pipeline (`domain/sms/`)

This is the core of the app; read this before touching any of these files.

**Pipeline order:** `SmsFilter` → `TransactionExtractor` → `TransactionCategorizer` → `SmsProcessor` (orchestrator) → `Transaction` saved via `TransactionRepository`.

**Filtering (`SmsFilter.kt`, `SmsConstants.kt`, `SmsTransactionalSenderCodes.kt`)**
- First gate is sender allowlist (`SmsTransactionalSenderCodes.isAllowedSender`) — cheap rejection of most inbox noise before any regex runs.
- Then cheap keyword exclusions (OTP, failed-transaction keywords) before any regex.
- Must match a transaction keyword pattern, then must **not** match any of: reversed-transaction, EMI-conversion, credit-card-statement, pending-transaction, fraud-consent, or promotional regex patterns.
- All keyword/pattern lists live in `SmsConstants.kt` — add new exclusion terms there, not inline in `SmsFilter`.

**Sender → bank resolution (`BankSenderDetector.kt`)**
- Maps TRAI-registered SMS sender codes (e.g. `VK-HDFCBK`) to a canonical bank key (e.g. `"HDFC"`) used throughout `SmsConstants.BANK_NAMES` and pattern comments.
- Strips operator prefixes, then matches longest token first (prevents short tokens like `SBI` false-matching inside longer sender codes).
- To add a new bank, add its sender tokens here, ordered longest-first per bank.

**Extraction (`TransactionExtractor.kt`, ~1800 lines) + `BankPatternRegistry.kt` (~1250 lines)**
- Bank-specific and generic regex patterns extract amount, merchant, account digits, transaction type, currency, reference number, etc. from the raw SMS body.
- Patterns are tried in order; a numbered fallback chain exists down to a generic pattern (`patternIndex == 7` = weak/generic match — flagged as `isWeakMatch` downstream and reported via `PatternReporter` for telemetry).
- `DynamicPatternEngine.kt` supports patterns synced from Supabase at runtime (`SmsPatternEntity`), layered on top of the hardcoded registry — check both when debugging a parsing failure.
- When a known bank sender's SMS fails to parse (amount is null/zero), it's reported anonymously (see Pattern Reporting below), never logged with raw content to any remote system.

**Categorization (`TransactionCategorizer.kt`)**
Priority order for assigning a category to a merchant, first match wins:
1. User-saved mapping (per-user override, via `getMerchantCategory` hook)
2. Remote merchant→category mappings (synced from Supabase, cached in Room)
3. Local hardcoded `SmsConstants.MERCHANT_CATEGORIES` map
4. Remote word→category mappings (synced from Supabase)
5. Local hardcoded `SmsConstants.WORD_CATEGORIES` map (final fallback)
6. Falls back to `"others"` if nothing matches

Matching: keys ≤4 chars use a word-boundary regex (avoids short strings matching inside longer words); longer keys use substring `contains`. Longer keys are tried first (sorted by length descending) so more specific matches win.

To add a new merchant or category rule: prefer adding to the remote Supabase-synced tables (so existing installs pick it up without an app update) over the local hardcoded maps in `SmsConstants.kt`; use the local maps for defaults/fallback only.

**Concurrency (`SmsProcessor.kt`)**
- Batch-processes SMS in chunks of 16 concurrently (`CONCURRENCY = 16`).
- `ProcessingCaches` pre-loads all DB lookups (account mappings, merchant categories/corrections) once per batch to avoid per-message DB round-trips — if you add a new per-message lookup, add it to this cache struct rather than querying inline.

**Pattern reporting (`PatternReporter.kt`, `SmsAnonymizer.kt`)**
- On parse failure for a known-bank sender, the raw SMS is converted to an anonymized structural "skeleton" (merchant/payee names collapsed to a placeholder) via `SmsAnonymizer.toSkeleton`, hashed (SHA-256), and queued to a `PatternReportWorker` that upserts a hit-counter row in Supabase (`unknown_patterns` table, see `supabase/migrations/20260405_unknown_patterns.sql`).
- **No raw SMS body or user identity is ever transmitted.** Preserve this invariant in any changes to this path.
- No-ops silently if Supabase isn't configured in the build.

**Multi-currency conversion (`ForexService.kt`)**
- Rates fetched live from `api.frankfurter.app`; on any failure, falls back to a hardcoded rate table (`fallbacks` map) covering ~19 common currencies.
- Unknown currency with no fallback: amount is returned unconverted rather than dropping the transaction.
- `normalizeCurrencyCode` treats `RS`, `INR`, `₹` (and blank) as INR; anything else is passed through if it looks like a 3-letter code.

## Do-Not-Touch / Be-Careful Zones

- `domain/sms/BankPatternRegistry.kt` and `TransactionExtractor.kt` — large, hand-tuned regex sets against real bank SMS formats. Don't "simplify" or reorder without running `SmsProcessingTest.kt` and testing against real sample messages (use `sms-tester.html`).
- `domain/sms/SmsAnonymizer.kt` and `PatternReporter.kt` — privacy-critical. Any change must preserve the "no raw SMS content or user identity leaves the device" guarantee.
- `receiver/SmsTransactionReceiver.kt` and anything touching `READ_SMS`/`RECEIVE_SMS` permissions — core data ingestion path, test carefully across Android versions.
- `ForexService.kt` fallback rate table — stale rates silently produce wrong INR amounts; if updating, update all currencies together, not piecemeal.

## Testing Expectations

- Changes to SMS filtering, extraction, or categorization logic should be covered in `SmsProcessingTest.kt` with real (anonymized) sample SMS bodies.
- Changes to `ForexService` conversion logic should be covered in `ForexServiceTest.kt`.
- Changes to anonymization/skeleton logic should be covered in `SmsAnonymizerTest.kt`.
- UI/Compose screens: no established test convention yet in this repo — use judgment.

## Database / Migrations

- Supabase migrations live in `supabase/migrations/`, named `YYYYMMDD_description.sql`.
- Existing tables: `unknown_patterns` (pattern-reporting telemetry, RLS-locked, no anon SELECT) and `wrong_sms` (user-reported misdetections, includes the detected/expected fields for comparison).
- Local persistence (accounts, transactions, budgets, custom categories, SMS patterns) is Room, in `data/local/db/` — see `AppDatabase.kt` and `DatabaseMigrations.kt` for schema versioning.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
|------|----------|
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
