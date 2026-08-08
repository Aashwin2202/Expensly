# Expensly

An Android app that automatically tracks expenses by reading bank and credit card transaction SMS messages — no manual entry required.

## Features

- **Automatic transaction detection** from bank/credit card SMS, with filtering for OTPs, promotions, failed/pending transactions, and fraud alerts
- **Merchant recognition & categorization** (food, groceries, shopping, travel, bills, health, entertainment, investment, etc.)
- **Accounts & cards overview** with per-account and per-card transaction history
- **Budgets** with category-level tracking
- **Insights** into spending patterns over time
- **Reminders** for upcoming/recurring payments
- **Weekly summaries** and an end-of-period "Wrapped" recap
- **Multi-currency support** with automatic conversion to INR

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **DI:** Hilt
- **Navigation:** Jetpack Navigation Compose
- **Backend:** Supabase (Postgres, migrations in [supabase/migrations](supabase/migrations))
- **Build:** Gradle (Kotlin DSL), compileSdk 35

## Project Structure

```
app/src/main/java/com/fintrackai/
├── data/           # Repositories, local/remote data sources
├── di/             # Hilt modules
├── domain/         # Business logic (accounts, categories, sms parsing, recurring, wrapped, etc.)
├── navigation/      # Nav graph and screen routes
├── notification/    # Notification scheduling/handling
├── receiver/         # Broadcast receivers (e.g. SMS)
├── ui/              # Compose screens, grouped by feature
└── work/            # Background workers
```

## Getting Started

1. Clone the repo and open it in Android Studio.
2. Add your local signing/config values to `local.properties` as needed.
3. Configure Supabase credentials (see `supabase/migrations` for schema).
4. Build and run:
   ```
   ./gradlew assembleDebug
   ```

## License

No license specified yet.
