package com.fintrackai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sms_dedupe_hash"], unique = true),
        Index(value = ["type", "countInStats", "date"]),
        Index(value = ["accounts"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val merchant: String,
    val amount: Double,
    val type: String,
    val category: String,
    val date: String,
    @ColumnInfo(defaultValue = "'00:00'")
    val time: String = "00:00",
    val accounts: String,
    val reference: String?,
    val originalSms: String?,
    val smsSender: String?,
    @ColumnInfo(defaultValue = "1")
    val countInStats: Int = 1,
    @ColumnInfo(name = "sms_dedupe_hash")
    val smsDedupeHash: String? = null,
    @ColumnInfo(name = "link_group_id")
    val linkGroupId: String? = null,
    @ColumnInfo(name = "link_suppressed", defaultValue = "0")
    val linkSuppressed: Int = 0,
    @ColumnInfo(name = "link_stashed_amount")
    val linkStashedAmount: Double? = null,
    @ColumnInfo(name = "link_stashed_type")
    val linkStashedType: String? = null,
    @ColumnInfo(name = "needs_reprocess", defaultValue = "0")
    val needsReprocess: Int = 0
)

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0")
    val hidden: Int = 0
)

@Entity(tableName = "merchant_category_mappings")
data class MerchantCategoryMappingEntity(
    @PrimaryKey
    val merchant: String,
    val category: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL")
    val countInStats: Int? = null
)

@Entity(
    tableName = "account_mappings",
    primaryKeys = ["last4Digits", "bankName"]
)
data class AccountMappingEntity(
    val last4Digits: String,
    val bankName: String,
    val accountType: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL")
    val availableBalance: Double? = null,
    @ColumnInfo(defaultValue = "NULL")
    val balanceUpdatedAt: Long? = null,
    @ColumnInfo(name = "is_confident", defaultValue = "1")
    val isConfident: Boolean = true
)

@Entity(tableName = "merchant_name_mappings")
data class MerchantNameMappingEntity(
    /** Normalized (lowercase, trimmed) original merchant name — used as lookup key. */
    @PrimaryKey
    val originalName: String,
    val correctedName: String,
    val updatedAt: Long
)

@Entity(tableName = "budget_settings")
data class BudgetSettingsEntity(
    @PrimaryKey
    val key: String,
    val value: Double
)

@Entity(tableName = "dismissed_recurring", primaryKeys = ["merchant", "amount"])
data class DismissedRecurringEntity(
    val merchant: String,
    val amount: Double
)

@Entity(tableName = "remote_merchant_category_mappings")
data class RemoteMerchantCategoryEntity(
    @PrimaryKey
    val id: String,
    val keyword: String,
    val category: String,
    @ColumnInfo(name = "match_type")
    val matchType: String,
    val version: Int,
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    val frequency: String,
    @ColumnInfo(name = "reminder_date")
    val reminderDate: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "paid_on")
    val paidOn: String? = null
)
