package com.fintrackai.domain.transactions

object TransactionExportImportConstants {
    const val CSV_MIME_TYPE = "text/csv"
    const val EXPORT_FILE_NAME_PREFIX = "fintrackai_transactions_"
    const val EXPORT_FILE_SUFFIX = ".csv"

    const val COL_ID = "id"
    const val COL_MERCHANT = "merchant"
    const val COL_AMOUNT = "amount"
    const val COL_TYPE = "type"
    const val COL_CATEGORY = "category"
    const val COL_DATE = "date"
    const val COL_TIME = "time"
    const val COL_ACCOUNTS = "accounts"
    const val COL_REFERENCE = "reference"
    const val COL_COUNT_IN_STATS = "countInStats"
    const val COL_ORIGINAL_SMS = "originalSms"
    const val COL_SMS_SENDER = "smsSender"
    const val COL_SMS_DEDUPE_HASH = "smsDedupeHash"

    val HEADER_ORDER = listOf(
        COL_ID,
        COL_MERCHANT,
        COL_AMOUNT,
        COL_TYPE,
        COL_CATEGORY,
        COL_DATE,
        COL_TIME,
        COL_ACCOUNTS,
        COL_REFERENCE,
        COL_COUNT_IN_STATS,
        COL_ORIGINAL_SMS,
        COL_SMS_SENDER,
        COL_SMS_DEDUPE_HASH
    )
}
