package com.fintrackai.data.remote

import com.fintrackai.data.local.db.SmsPatternEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteSmsPattern(
    val id: String,
    val regex: String,
    @SerialName("regex_options") val regexOptions: List<String>,
    @SerialName("transaction_type") val transactionType: String,
    val priority: Double,
    @SerialName("group_amount") val groupAmount: String? = null,
    @SerialName("group_currency") val groupCurrency: String? = null,
    @SerialName("group_date") val groupDate: String? = null,
    @SerialName("group_account") val groupAccount: String? = null,
    @SerialName("group_merchant") val groupMerchant: String? = null,
    @SerialName("group_reference") val groupReference: String? = null,
    @SerialName("group_card_number") val groupCardNumber: String? = null,
    @SerialName("group_bank_name") val groupBankName: String? = null,
    @SerialName("account_label_type") val accountLabelType: String? = null,
    @SerialName("default_currency") val defaultCurrency: String = "INR",
    @SerialName("clean_merchant") val cleanMerchant: Boolean = true,
    /**
     * Optional static merchant template. `{groupName}` placeholders are substituted from the
     * regex match. Takes priority over [groupMerchant] when non-null.
     * Example: `"Withdrawal (Card {cardNum})"`.
     */
    val merchant: String? = null,
    val version: Int,
    /** Privacy-safe SMS skeleton produced by SmsAnonymizer — stored for reference only */
    @SerialName("sample_sms") val sampleSms: String? = null,
    /**
     * Optional TRAI sender ID (e.g. "AD-AXISBK", "VK-HDFCBK"). When set, the pattern is only
     * tried when the incoming SMS sender resolves to the same bank via [BankSenderDetector].
     * Null = generic, tried for all banks.
     */
    @SerialName("sender_id") val senderId: String? = null,
    /** Named capture group for the post-transaction account balance (e.g. "balance"). */
    @SerialName("group_balance") val groupBalance: String? = null,
    /** Named capture group for the available credit limit on a card (e.g. "creditLimit"). */
    @SerialName("group_credit_limit") val groupCreditLimit: String? = null
) {
    fun toEntity(): SmsPatternEntity = SmsPatternEntity(
        id = id,
        regex = regex,
        regexOptions = regexOptions.joinToString(","),
        transactionType = transactionType,
        priority = priority,
        groupAmount = groupAmount,
        groupCurrency = groupCurrency,
        groupDate = groupDate,
        groupAccount = groupAccount,
        groupMerchant = groupMerchant,
        groupReference = groupReference,
        groupCardNumber = groupCardNumber,
        groupBankName = groupBankName,
        accountLabelType = accountLabelType,
        defaultCurrency = defaultCurrency,
        cleanMerchant = cleanMerchant,
        merchant = merchant,
        version = version,
        syncedAt = System.currentTimeMillis(),
        sampleSms = sampleSms,
        senderId = senderId,
        groupBalance = groupBalance,
        groupCreditLimit = groupCreditLimit
    )
}
