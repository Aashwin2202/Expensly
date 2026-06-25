package com.fintrackai.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN sms_dedupe_hash TEXT DEFAULT NULL")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sms_dedupe_hash ON transactions(sms_dedupe_hash)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN link_peer_id TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN link_suppressed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN link_stashed_amount REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN link_stashed_type TEXT DEFAULT NULL")
    }
}

/**
 * Replaces the 1:1 [link_peer_id] column with a group-based [link_group_id].
 * Each existing linked pair is assigned a shared UUID so the model naturally
 * supports one primary with multiple secondaries.
 *
 * Migration steps:
 *  1. Add [link_group_id] column.
 *  2. For every primary row (link_peer_id IS NOT NULL AND link_suppressed = 0)
 *     generate a UUID, stamp it on the primary, then find the secondary that
 *     pointed back at the primary and stamp the same UUID on it.
 *  3. Drop [link_peer_id] by recreating the table without it.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE custom_categories ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account_mappings ADD COLUMN availableBalance REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE account_mappings ADD COLUMN balanceUpdatedAt INTEGER DEFAULT NULL")
    }
}

/**
 * Replaces single-column PK [last4Digits] with a composite PK [last4Digits, bankName, accountType].
 * This allows two different cards/accounts that happen to share the same last 4 digits
 * (e.g. HDFC Credit Card 1234 vs ICICI Debit Card 1234) to coexist as separate entries.
 * SQLite does not support ALTER TABLE for PK changes, so the table is recreated.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE account_mappings_new (
                last4Digits TEXT NOT NULL,
                bankName TEXT NOT NULL,
                accountType TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                availableBalance REAL DEFAULT NULL,
                balanceUpdatedAt INTEGER DEFAULT NULL,
                PRIMARY KEY(last4Digits, bankName)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO account_mappings_new
            SELECT last4Digits, bankName, accountType, updatedAt, availableBalance, balanceUpdatedAt
            FROM account_mappings
        """.trimIndent())
        db.execSQL("DROP TABLE account_mappings")
        db.execSQL("ALTER TABLE account_mappings_new RENAME TO account_mappings")
    }
}

// No schema change between 7 and 8; migration required to prevent destructive fallback.
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS merchant_name_mappings (
                originalName TEXT NOT NULL PRIMARY KEY,
                correctedName TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sms_patterns (
                id TEXT NOT NULL PRIMARY KEY,
                regex TEXT NOT NULL,
                regex_options TEXT NOT NULL,
                transaction_type TEXT NOT NULL,
                priority REAL NOT NULL,
                group_amount TEXT,
                group_currency TEXT,
                group_date TEXT,
                group_account TEXT,
                group_merchant TEXT,
                group_reference TEXT,
                group_card_number TEXT,
                default_currency TEXT NOT NULL,
                clean_merchant INTEGER NOT NULL DEFAULT 1,
                clean_account INTEGER NOT NULL DEFAULT 1,
                extract_vpa_as_merchant INTEGER NOT NULL DEFAULT 0,
                extract_bank_name INTEGER NOT NULL DEFAULT 0,
                account_template TEXT,
                version INTEGER NOT NULL,
                synced_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_patterns ADD COLUMN group_bank_name TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE sms_patterns ADD COLUMN account_label_type TEXT DEFAULT NULL")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recreate sms_patterns without the removed post-processing columns and with sample_sms added
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sms_patterns_new (
                id TEXT NOT NULL PRIMARY KEY,
                regex TEXT NOT NULL,
                regex_options TEXT NOT NULL,
                transaction_type TEXT NOT NULL,
                priority REAL NOT NULL,
                group_amount TEXT,
                group_currency TEXT,
                group_date TEXT,
                group_account TEXT,
                group_merchant TEXT,
                group_reference TEXT,
                group_card_number TEXT,
                default_currency TEXT NOT NULL,
                clean_merchant INTEGER NOT NULL DEFAULT 1,
                version INTEGER NOT NULL,
                synced_at INTEGER NOT NULL,
                sample_sms TEXT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO sms_patterns_new
            SELECT id, regex, regex_options, transaction_type, priority,
                   group_amount, group_currency, group_date, group_account,
                   group_merchant, group_reference, group_card_number,
                   default_currency, clean_merchant, version, synced_at, NULL
            FROM sms_patterns
        """.trimIndent())
        db.execSQL("DROP TABLE sms_patterns")
        db.execSQL("ALTER TABLE sms_patterns_new RENAME TO sms_patterns")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS remote_merchant_category_mappings (
                id TEXT NOT NULL PRIMARY KEY,
                keyword TEXT NOT NULL,
                category TEXT NOT NULL,
                match_type TEXT NOT NULL,
                version INTEGER NOT NULL,
                synced_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add new column
        db.execSQL("ALTER TABLE transactions ADD COLUMN link_group_id TEXT DEFAULT NULL")

        // 2. Assign group IDs to existing linked pairs
        val cursor = db.query(
            "SELECT id, link_peer_id FROM transactions WHERE link_peer_id IS NOT NULL AND link_suppressed = 0"
        )
        cursor.use {
            while (it.moveToNext()) {
                val primaryId = it.getString(0)
                val secondaryId = it.getString(1)
                val groupId = UUID.randomUUID().toString()
                db.execSQL(
                    "UPDATE transactions SET link_group_id = ? WHERE id = ?",
                    arrayOf(groupId, primaryId)
                )
                db.execSQL(
                    "UPDATE transactions SET link_group_id = ? WHERE id = ?",
                    arrayOf(groupId, secondaryId)
                )
            }
        }

        // 3. Recreate table without link_peer_id
        db.execSQL("""
            CREATE TABLE transactions_new (
                id TEXT PRIMARY KEY NOT NULL,
                merchant TEXT NOT NULL,
                amount REAL NOT NULL,
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                date TEXT NOT NULL,
                time TEXT NOT NULL DEFAULT '00:00',
                accounts TEXT NOT NULL,
                reference TEXT,
                originalSms TEXT,
                smsSender TEXT,
                countInStats INTEGER NOT NULL DEFAULT 1,
                sms_dedupe_hash TEXT DEFAULT NULL,
                link_group_id TEXT DEFAULT NULL,
                link_suppressed INTEGER NOT NULL DEFAULT 0,
                link_stashed_amount REAL DEFAULT NULL,
                link_stashed_type TEXT DEFAULT NULL
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO transactions_new
            SELECT id, merchant, amount, type, category, date, time, accounts,
                   reference, originalSms, smsSender, countInStats, sms_dedupe_hash,
                   link_group_id, link_suppressed, link_stashed_amount, link_stashed_type
            FROM transactions
        """.trimIndent())

        db.execSQL("DROP TABLE transactions")
        db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sms_dedupe_hash ON transactions(sms_dedupe_hash)"
        )
    }
}
