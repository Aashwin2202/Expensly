package com.fintrackai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account_mappings ADD COLUMN is_confident INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN paid_on TEXT")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_patterns ADD COLUMN merchant TEXT")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE merchant_category_mappings ADD COLUMN countInStats INTEGER DEFAULT NULL")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_patterns ADD COLUMN sender_id TEXT DEFAULT NULL")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_patterns ADD COLUMN group_balance TEXT DEFAULT NULL")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sms_patterns ADD COLUMN group_credit_limit TEXT DEFAULT NULL")
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_countInStats_date ON transactions(type, countInStats, date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accounts ON transactions(accounts)")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN needs_reprocess INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        TransactionEntity::class,
        CustomCategoryEntity::class,
        MerchantCategoryMappingEntity::class,
        MerchantNameMappingEntity::class,
        AccountMappingEntity::class,
        BudgetSettingsEntity::class,
        DismissedRecurringEntity::class,
        ReminderEntity::class,
        SmsPatternEntity::class,
        RemoteMerchantCategoryEntity::class
    ],
    version = 22,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun merchantMappingDao(): MerchantMappingDao
    abstract fun merchantNameMappingDao(): MerchantNameMappingDao
    abstract fun accountMappingDao(): AccountMappingDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun reminderDao(): ReminderDao
    abstract fun smsPatternDao(): SmsPatternDao
    abstract fun remoteMerchantCategoryDao(): RemoteMerchantCategoryDao
}
