package com.fintrackai.domain.transactions

import com.fintrackai.domain.model.Transaction
import java.nio.charset.StandardCharsets
object TransactionCsvHelper {

    fun toCsvByteArray(rows: List<Transaction>): ByteArray {
        val sb = StringBuilder()
        sb.appendLine(TransactionExportImportConstants.HEADER_ORDER.joinToString(","))
        for (tx in rows) {
            sb.appendLine(rowToLine(tx))
        }
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun rowToLine(tx: Transaction): String {
        return listOf(
            tx.id,
            tx.merchant,
            tx.amount.toString(),
            tx.type,
            tx.category,
            tx.date,
            tx.time,
            tx.accounts,
            tx.reference ?: "",
            if (tx.countInStats) "1" else "0",
            tx.originalSms ?: "",
            tx.smsSender ?: "",
            tx.smsDedupeHash ?: ""
        ).joinToString(",") { escapeField(it) }
    }

    private fun escapeField(value: String): String {
        if (value.indexOfAny(charArrayOf(',', '"', '\n', '\r')) < 0) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    data class ParseSummary(
        val transactions: List<Transaction>,
        val dataRowsRead: Int,
        val skippedMalformedRows: Int,
        val invalidHeader: Boolean = false
    )

    fun parseTransactionsCsv(bytes: ByteArray): ParseSummary {
        val text = bytes.toString(StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ParseSummary(emptyList(), 0, 0, invalidHeader = true)
        }
        val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
        val index = buildHeaderIndex(header)
        if (index[TransactionExportImportConstants.COL_ID] == null ||
            index[TransactionExportImportConstants.COL_MERCHANT] == null ||
            index[TransactionExportImportConstants.COL_AMOUNT] == null ||
            index[TransactionExportImportConstants.COL_TYPE] == null ||
            index[TransactionExportImportConstants.COL_CATEGORY] == null ||
            index[TransactionExportImportConstants.COL_DATE] == null ||
            index[TransactionExportImportConstants.COL_TIME] == null ||
            index[TransactionExportImportConstants.COL_ACCOUNTS] == null
        ) {
            return ParseSummary(emptyList(), 0, lines.size - 1, invalidHeader = true)
        }
        val out = mutableListOf<Transaction>()
        var skipped = 0
        var dataRows = 0
        for (i in 1 until lines.size) {
            dataRows++
            val fields = parseCsvLine(lines[i])
            val tx = rowToTransaction(fields, index)
            if (tx == null) {
                skipped++
            } else {
                out.add(tx)
            }
        }
        return ParseSummary(out, dataRows, skipped)
    }

    private fun buildHeaderIndex(header: List<String>): Map<String, Int> {
        val m = HashMap<String, Int>()
        header.forEachIndexed { i, name ->
            if (name.isNotBlank()) m[name] = i
        }
        return m
    }

    private fun rowToTransaction(fields: List<String>, idx: Map<String, Int>): Transaction? {
        fun get(col: String): String? {
            val i = idx[col] ?: return null
            return fields.getOrNull(i)
        }
        val id = get(TransactionExportImportConstants.COL_ID)?.trim().orEmpty()
        val merchant = get(TransactionExportImportConstants.COL_MERCHANT)?.trim().orEmpty()
        val amountStr = get(TransactionExportImportConstants.COL_AMOUNT)?.trim().orEmpty()
        val type = get(TransactionExportImportConstants.COL_TYPE)?.trim().orEmpty()
        val category = get(TransactionExportImportConstants.COL_CATEGORY)?.trim().orEmpty()
        val date = get(TransactionExportImportConstants.COL_DATE)?.trim().orEmpty()
        val time = get(TransactionExportImportConstants.COL_TIME)?.trim().orEmpty()
        val accounts = get(TransactionExportImportConstants.COL_ACCOUNTS)?.trim().orEmpty()
        if (id.isEmpty() || merchant.isEmpty() || amountStr.isEmpty() || type.isEmpty() ||
            category.isEmpty() || date.isEmpty() || time.isEmpty() || accounts.isEmpty()
        ) {
            return null
        }
        val amount = amountStr.toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val reference = get(TransactionExportImportConstants.COL_REFERENCE)?.trim()?.takeIf { it.isNotEmpty() }
        val countRaw = get(TransactionExportImportConstants.COL_COUNT_IN_STATS)?.trim()
        val countInStats = when (countRaw?.lowercase()) {
            "0", "false" -> false
            else -> true
        }
        val originalSms = get(TransactionExportImportConstants.COL_ORIGINAL_SMS)?.takeIf { it.isNotEmpty() }
        val smsSender = get(TransactionExportImportConstants.COL_SMS_SENDER)?.takeIf { it.isNotEmpty() }
        val smsDedupeHash = get(TransactionExportImportConstants.COL_SMS_DEDUPE_HASH)?.trim()?.takeIf { it.isNotEmpty() }
        return Transaction(
            id = id,
            merchant = merchant,
            amount = amount,
            type = type,
            category = category,
            date = date,
            time = time,
            accounts = accounts,
            reference = reference,
            countInStats = countInStats,
            originalSms = originalSms,
            smsSender = smsSender,
            smsDedupeHash = smsDedupeHash
        )
    }

    /**
     * RFC 4180–style line parse: comma-separated with optional double-quoted fields.
     */
    fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val len = line.length
        val cur = StringBuilder()
        while (i < len) {
            when (val c = line[i]) {
                '"' -> {
                    i++
                    while (i < len) {
                        when (line[i]) {
                            '"' -> {
                                if (i + 1 < len && line[i + 1] == '"') {
                                    cur.append('"')
                                    i += 2
                                } else {
                                    i++
                                    break
                                }
                            }
                            else -> {
                                cur.append(line[i])
                                i++
                            }
                        }
                    }
                }
                ',' -> {
                    out.add(cur.toString())
                    cur.clear()
                    i++
                }
                else -> {
                    cur.append(c)
                    i++
                }
            }
        }
        out.add(cur.toString())
        return out
    }
}
