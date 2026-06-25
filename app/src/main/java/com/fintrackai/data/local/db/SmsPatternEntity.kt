package com.fintrackai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_patterns")
data class SmsPatternEntity(
    @PrimaryKey
    val id: String,
    val regex: String,
    @ColumnInfo(name = "regex_options")
    val regexOptions: String,          // comma-separated: "IGNORE_CASE", "DOT_MATCHES_ALL", "MULTILINE"
    @ColumnInfo(name = "transaction_type")
    val transactionType: String,       // "debit", "credit", "detect"
    val priority: Double,

    // Named capture groups — null means "not present in this pattern"
    @ColumnInfo(name = "group_amount")
    val groupAmount: String?,
    @ColumnInfo(name = "group_currency")
    val groupCurrency: String?,
    @ColumnInfo(name = "group_date")
    val groupDate: String?,
    @ColumnInfo(name = "group_account")
    val groupAccount: String?,
    @ColumnInfo(name = "group_merchant")
    val groupMerchant: String?,
    @ColumnInfo(name = "group_reference")
    val groupReference: String?,
    @ColumnInfo(name = "group_card_number")
    val groupCardNumber: String?,
    @ColumnInfo(name = "group_bank_name")
    val groupBankName: String?,
    // How to label the account in fullAccount — e.g. "Card", "Debit Card", "Credit Card", "Acct"
    @ColumnInfo(name = "account_label_type")
    val accountLabelType: String?,

    @ColumnInfo(name = "default_currency")
    val defaultCurrency: String,
    @ColumnInfo(name = "clean_merchant")
    val cleanMerchant: Boolean,

    /**
     * Optional static merchant template with `{groupName}` placeholders that are substituted
     * from the regex match at runtime. When non-null, this takes priority over [groupMerchant].
     * Example: `"Withdrawal (Card {cardNum})"` — `{cardNum}` is replaced by the named capture
     * group value `cardNum` from the matched regex.
     */
    @ColumnInfo(name = "merchant")
    val merchant: String? = null,

    val version: Int,
    @ColumnInfo(name = "synced_at")
    val syncedAt: Long,

    /** Privacy-safe skeleton from SmsAnonymizer — stored for reference/debugging only */
    @ColumnInfo(name = "sample_sms")
    val sampleSms: String? = null,

    /**
     * Optional TRAI sender ID (e.g. "AD-AXISBK", "VK-HDFCBK"). When non-null, the pattern is
     * only tried when [BankSenderDetector.detect] resolves the incoming SMS sender to the same
     * bank as this sender_id. Null means the pattern is generic and tried for every bank.
     */
    @ColumnInfo(name = "sender_id")
    val senderId: String? = null,

    /** Named capture group for the account balance after the transaction (e.g. "balance"). */
    @ColumnInfo(name = "group_balance")
    val groupBalance: String? = null,

    /**
     * Named capture group for the available credit limit on a card (e.g. "creditLimit").
     * Distinct from [groupBalance]: credit-card SMS report "available credit limit" rather
     * than an account balance.
     */
    @ColumnInfo(name = "group_credit_limit")
    val groupCreditLimit: String? = null
)
