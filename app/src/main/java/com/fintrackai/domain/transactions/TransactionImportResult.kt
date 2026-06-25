package com.fintrackai.domain.transactions

data class TransactionImportResult(
    val dataRowsRead: Int,
    val rowsParsedOk: Int,
    val rowsInserted: Int,
    val rowsSkippedMalformed: Int,
    val invalidHeader: Boolean
)
