package com.fintrackai.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE amount > 0
        ORDER BY date DESC, time DESC
        """
    )
    fun getAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE amount > 0
        ORDER BY date DESC, time DESC
        LIMIT :limit
        """
    )
    fun getRecentFlow(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT DISTINCT accounts FROM transactions WHERE TRIM(accounts) != '' ORDER BY accounts ASC")
    fun getDistinctAccountsFlow(): Flow<List<String>>

    @Query("""
        SELECT
            accounts,
            COALESCE(SUM(CASE WHEN strftime('%Y-%m', date) = :monthKey THEN amount ELSE 0 END), 0) AS monthTotal,
            COALESCE(SUM(amount), 0) AS allTimeTotal
        FROM transactions
        WHERE type = 'debit' AND amount > 0 AND countInStats = 1 AND TRIM(accounts) != ''
        GROUP BY accounts
    """)
    fun getAccountTotalsFlow(monthKey: String): Flow<List<AccountTotalsRow>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE amount > 0
        ORDER BY date DESC, time DESC LIMIT :limit
        """
    )
    suspend fun getAll(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE amount > 0 ORDER BY date ASC, time ASC")
    suspend fun getAllForExport(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE reference = :reference LIMIT 1")
    suspend fun findByReference(reference: String): TransactionEntity?

    @Query("""
        UPDATE transactions SET
            merchant  = CASE WHEN merchant  = 'Unknown' AND :merchant  != 'Unknown' THEN :merchant  ELSE merchant  END,
            accounts  = CASE WHEN accounts  = 'Unknown' AND :accounts  != 'Unknown' THEN :accounts  ELSE accounts  END,
            category  = CASE WHEN category  = 'Unknown' AND :category  != 'Unknown' THEN :category  ELSE category  END
        WHERE reference = :reference
    """)
    suspend fun mergeMissingFieldsByReference(reference: String, merchant: String, accounts: String, category: String)

    @Query("SELECT * FROM transactions WHERE sms_dedupe_hash = :hash LIMIT 1")
    suspend fun findBySmsDedupeHash(hash: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE IFNULL(smsSender, '') = :sender AND originalSms IS NOT NULL
        """
    )
    suspend fun listBySenderForSmsDedupe(sender: String): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE originalSms IS NOT NULL
          AND date = :date
          AND amount = :amount
          AND type = :type
          AND IFNULL(smsSender, '') = :sender
        """
    )
    suspend fun listForSmsFuzzyDedupe(
        sender: String,
        date: String,
        amount: Double,
        type: String
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE originalSms IS NOT NULL AND sms_dedupe_hash IS NULL")
    suspend fun listTransactionsNeedingSmsDedupeHash(): List<TransactionEntity>

    @Query("SELECT reference FROM transactions WHERE reference IS NOT NULL")
    suspend fun getAllReferences(): List<String>

    @Query("SELECT sms_dedupe_hash FROM transactions WHERE sms_dedupe_hash IS NOT NULL")
    suspend fun getAllSmsDedupeHashes(): List<String>

    @Query("UPDATE transactions SET sms_dedupe_hash = :hash WHERE id = :id")
    suspend fun updateSmsDedupeHash(id: String, hash: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'credit' AND countInStats = 1 THEN amount END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'debit' AND countInStats = 1 THEN amount END), 0) AS expense
        FROM transactions WHERE strftime('%Y-%m', date) = :monthKey
    """)
    fun getMonthlyStatsFlow(monthKey: String): Flow<MonthlyStatsRow>

    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'credit' AND countInStats = 1 THEN amount END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'debit' AND countInStats = 1 THEN amount END), 0) AS expense
        FROM transactions WHERE strftime('%Y-%m', date) = :monthKey
    """)
    suspend fun getMonthlyStats(monthKey: String): MonthlyStatsRow

    @Query("""
        SELECT LOWER(category) AS category, SUM(amount) AS amount
        FROM transactions
        WHERE type = 'debit' AND (countInStats = 1 OR LOWER(category) = 'investment')
        GROUP BY LOWER(category) ORDER BY amount DESC
    """)
    suspend fun getCategoryStatsAll(): List<CategoryStatRow>

    @Query("""
        SELECT LOWER(category) AS category, SUM(amount) AS amount
        FROM transactions
        WHERE type = 'debit' AND (countInStats = 1 OR LOWER(category) = 'investment') AND strftime('%Y-%m', date) = :monthKey
        GROUP BY LOWER(category) ORDER BY amount DESC
    """)
    fun getCategoryStatsFlow(monthKey: String): Flow<List<CategoryStatRow>>

    @Query("""
        SELECT LOWER(category) AS category, SUM(amount) AS amount
        FROM transactions
        WHERE type = 'debit' AND (countInStats = 1 OR LOWER(category) = 'investment') AND strftime('%Y-%m', date) = :monthKey
        GROUP BY LOWER(category) ORDER BY amount DESC
    """)
    suspend fun getCategoryStats(monthKey: String): List<CategoryStatRow>

    @Query("""
        SELECT merchant, SUM(amount) AS amount, COUNT(*) AS transactionCount
        FROM transactions
        WHERE type = 'debit'
        GROUP BY merchant ORDER BY amount DESC
    """)
    suspend fun getMerchantStatsAll(): List<MerchantStatRow>

    @Query("""
        SELECT merchant, SUM(amount) AS amount, COUNT(*) AS transactionCount
        FROM transactions
        WHERE type = 'debit' AND countInStats = 1 AND strftime('%Y-%m', date) = :monthKey
        GROUP BY merchant ORDER BY amount DESC
    """)
    fun getMerchantStatsFlow(monthKey: String): Flow<List<MerchantStatRow>>

    @Query("""
        SELECT merchant, SUM(amount) AS amount, COUNT(*) AS transactionCount
        FROM transactions
        WHERE type = 'debit' AND countInStats = 1 AND strftime('%Y-%m', date) = :monthKey
        GROUP BY merchant ORDER BY amount DESC
    """)
    suspend fun getMerchantStats(monthKey: String): List<MerchantStatRow>

    @Query("""
        SELECT merchant, SUM(amount) AS amount, COUNT(*) AS transactionCount
        FROM transactions
        WHERE type = 'debit' AND strftime('%Y-%m', date) = :monthKey
        GROUP BY merchant ORDER BY amount DESC
    """)
    suspend fun getMerchantStatsAllCountInStats(monthKey: String): List<MerchantStatRow>

    @Query("SELECT MIN(date) AS firstDate FROM transactions WHERE type='debit' AND countInStats=1 AND merchant = :merchant")
    suspend fun getMerchantFirstDate(merchant: String): String?

    @Query("""
        SELECT strftime('%Y-%m', date) AS monthKey, COALESCE(SUM(amount), 0) AS total
        FROM transactions WHERE type='debit' AND countInStats=1 AND merchant = :merchant
        GROUP BY monthKey
    """)
    suspend fun getMerchantMonthlyTotals(merchant: String): List<MonthTotalRow>

    @Query(
        """
        SELECT * FROM transactions
        WHERE merchant = :merchant AND amount > 0
        ORDER BY date DESC, time DESC
        """
    )
    suspend fun getByMerchant(merchant: String): List<TransactionEntity>

    @Query("SELECT MIN(date) AS firstDate FROM transactions WHERE type='debit' AND (countInStats=1 OR LOWER(category)='investment') AND LOWER(category) = :category")
    suspend fun getCategoryFirstDate(category: String): String?

    @Query("""
        SELECT strftime('%Y-%m', date) AS monthKey, COALESCE(SUM(amount), 0) AS total
        FROM transactions WHERE type='debit' AND (countInStats=1 OR LOWER(category)='investment') AND LOWER(category) = :category
        GROUP BY monthKey
    """)
    suspend fun getCategoryMonthlyTotals(category: String): List<MonthTotalRow>

    @Query(
        """
        SELECT * FROM transactions
        WHERE LOWER(category) = :category AND amount > 0
        ORDER BY date DESC, time DESC
        """
    )
    suspend fun getByCategory(category: String): List<TransactionEntity>

    @Query("UPDATE transactions SET category = :newCategory WHERE LOWER(category) = LOWER(:oldCategory)")
    suspend fun bulkUpdateCategory(oldCategory: String, newCategory: String)

    @Query("SELECT MIN(date) AS firstDate FROM transactions WHERE type='debit' AND countInStats=1 AND date IS NOT NULL")
    suspend fun getFirstDebitDate(): String?

    @Query("""
        SELECT COALESCE(SUM(amount), 0) AS total FROM transactions
        WHERE type='debit' AND countInStats=1 AND strftime('%Y', date) = :year AND strftime('%m', date) = :month
    """)
    suspend fun getMonthExpenseTotal(year: String, month: String): Double

    @Query("SELECT COALESCE(SUM(amount), 0) AS total FROM transactions WHERE type='debit' AND countInStats=1 AND date = :date")
    suspend fun getDaySpending(date: String): Double

    @Query("""
        SELECT merchant, COUNT(*) AS transactionCount, SUM(amount) AS totalAmount
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND strftime('%Y', date) = :year AND strftime('%m', date) = :month
        GROUP BY merchant HAVING COUNT(*) >= 5 ORDER BY transactionCount DESC, totalAmount DESC LIMIT 5
    """)
    suspend fun getRepeatedMerchants(year: String, month: String): List<RepeatedMerchantRow>

    @Query("""
        SELECT merchant, amount, strftime('%Y-%m', date) AS month, date AS txDate
        FROM transactions
        WHERE type = 'debit' AND amount > 0 AND IFNULL(link_suppressed, 0) = 0
    """)
    suspend fun getAllDebitGroupingData(): List<RecurringDataRow>

    @Query("""
        SELECT merchant, amount, strftime('%Y-%m', date) AS month, date AS txDate
        FROM transactions
        WHERE type = 'debit' AND amount > 0 AND date >= :minDate AND IFNULL(link_suppressed, 0) = 0
    """)
    suspend fun getDebitGroupingDataSince(minDate: String): List<RecurringDataRow>

    @Query("""
        SELECT merchant, amount, strftime('%Y-%m', date) AS month, date AS txDate
        FROM transactions
        WHERE type = 'debit' AND amount > 0 AND date >= :minDate AND date < :maxDateExclusive AND IFNULL(link_suppressed, 0) = 0
    """)
    suspend fun getDebitGroupingDataBetween(minDate: String, maxDateExclusive: String): List<RecurringDataRow>

    @Query(
        """
        SELECT * FROM transactions
        WHERE amount > 0
          AND type = :wantType
          AND id != :excludeId
          AND IFNULL(link_suppressed, 0) = 0
        ORDER BY date DESC, time DESC
        """
    )
    suspend fun listLinkCandidates(excludeId: String, wantType: String): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE link_group_id = :groupId
          AND IFNULL(link_suppressed, 0) = 1
        """
    )
    suspend fun listSecondariesForGroup(groupId: String): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE link_group_id = :groupId
          AND IFNULL(link_suppressed, 0) = 0
        LIMIT 1
        """
    )
    suspend fun getPrimaryForGroup(groupId: String): TransactionEntity?

    // Time-based pattern queries
    @Query("""
        SELECT COUNT(*) AS count, SUM(amount) AS totalAmount FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate
    """)
    suspend fun getDebitTotalInRange(startDate: String, endDate: String): CountAmountRow

    @Query("""
        SELECT COUNT(*) AS count, SUM(amount) AS totalAmount FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate
        AND ((CAST(SUBSTR(time,1,2) AS INTEGER) >= 22) OR (CAST(SUBSTR(time,1,2) AS INTEGER) >= 0 AND CAST(SUBSTR(time,1,2) AS INTEGER) < 4))
    """)
    suspend fun getLateNightSpending(startDate: String, endDate: String): CountAmountRow

    @Query("""
        SELECT COUNT(*) AS count, SUM(amount) AS totalAmount FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate
        AND CAST(strftime('%w', date) AS INTEGER) BETWEEN 1 AND 5
    """)
    suspend fun getWeekdaySpending(startDate: String, endDate: String): CountAmountRow

    @Query("""
        SELECT COUNT(*) AS count, SUM(amount) AS totalAmount FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate
        AND (CAST(strftime('%w', date) AS INTEGER) = 0 OR CAST(strftime('%w', date) AS INTEGER) = 6)
    """)
    suspend fun getWeekendSpending(startDate: String, endDate: String): CountAmountRow

    @Query("""
        SELECT amount FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate
        ORDER BY amount ASC
    """)
    suspend fun getDebitAmountsSorted(startDate: String, endDate: String): List<Double>

    @Query("""
        SELECT CAST(SUBSTR(time,1,2) AS INTEGER) AS hour, COUNT(*) AS count, SUM(amount) AS totalAmount
        FROM transactions WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate AND amount <= :maxAmount
        GROUP BY hour ORDER BY totalAmount DESC LIMIT 1
    """)
    suspend fun getPeakSpendingHour(startDate: String, endDate: String, maxAmount: Double): TimeDistRow?

    @Query("""
        SELECT CAST(SUBSTR(time,1,2) AS INTEGER) AS hour, COUNT(*) AS count, SUM(amount) AS totalAmount
        FROM transactions WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate AND amount <= :maxAmount
        GROUP BY hour ORDER BY hour ASC
    """)
    suspend fun getTimeDistribution(startDate: String, endDate: String, maxAmount: Double): List<TimeDistRow>

    @Query("""
        SELECT LOWER(category) AS category, SUM(amount) AS amount FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate
        GROUP BY LOWER(category) ORDER BY amount DESC
    """)
    suspend fun getCategoryStatsInRange(startDate: String, endDate: String): List<CategoryStatRow>

    @Query("""
        SELECT merchant, SUM(amount) AS amount FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date < :endDate
        GROUP BY merchant ORDER BY amount DESC LIMIT 1
    """)
    suspend fun getTopMerchantInRange(startDate: String, endDate: String): MerchantAmountRow?

    @Query("SELECT MIN(date) as minDate, MAX(date) as maxDate, COUNT(*) as count FROM transactions")
    suspend fun getDateRange(): DateRangeRow

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:merchant))
        """
    )
    suspend fun getMerchantTransactionCount(merchant: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:merchant)) AND id != :excludeId
        """
    )
    suspend fun getMerchantTransactionCountExcluding(merchant: String, excludeId: String): Int

    @Query(
        """
        UPDATE transactions SET category = :category
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:merchant))
        """
    )
    suspend fun updateMerchantCategory(merchant: String, category: String)

    @Query(
        """
        UPDATE transactions SET countInStats = :countInStats
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:merchant))
        """
    )
    suspend fun updateMerchantCountInStats(merchant: String, countInStats: Int)

    @Query(
        """
        UPDATE transactions SET merchant = :newName
        WHERE LOWER(TRIM(merchant)) = LOWER(TRIM(:oldName))
        """
    )
    suspend fun bulkUpdateMerchantName(oldName: String, newName: String)

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE type = 'debit'
          AND LOWER(TRIM(merchant)) = LOWER(TRIM(:merchant))
          AND ABS(amount - :amount) < 1.0
          AND strftime('%Y-%m', date) = :monthKey
        """
    )
    suspend fun countMatchingTransactionInMonth(merchant: String, amount: Double, monthKey: String): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query(
        """
        SELECT date, COALESCE(SUM(amount), 0) AS total
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND strftime('%Y-%m', date) = :monthKey
        GROUP BY date
        ORDER BY date ASC
        """
    )
    fun getDailyDebitTotalsByMonthFlow(monthKey: String): Flow<List<DayTotalRow>>

    @Query(
        """
        SELECT date, COALESCE(SUM(amount), 0) AS total
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND strftime('%Y-%m', date) = :monthKey
        GROUP BY date
        ORDER BY date ASC
        """
    )
    suspend fun getDailyDebitTotalsByMonth(monthKey: String): List<DayTotalRow>

    // --- Notification queries ---

    /** Daily summary: total spend, tx count, top category for a single date */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) AS totalSpend, COUNT(*) AS txCount
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND date = :date
    """)
    suspend fun getDailySummaryStats(date: String): DailySummaryRow

    @Query("""
        SELECT LOWER(category) AS category, SUM(amount) AS amount
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND date = :date
        GROUP BY LOWER(category) ORDER BY amount DESC LIMIT 1
    """)
    suspend fun getTopCategoryForDate(date: String): CategoryStatRow?

    @Query("""
        SELECT COUNT(DISTINCT merchant) FROM transactions
        WHERE type='debit' AND countInStats=1 AND date = :date
    """)
    suspend fun getDistinctMerchantCountForDate(date: String): Int

    /** Weekly summary: total spend for a date range */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) AS totalSpend, COUNT(*) AS txCount
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date <= :endDate
    """)
    suspend fun getWeeklySummaryStats(startDate: String, endDate: String): DailySummaryRow

    // --- Wrapped feature queries ---

    @Query("""
        SELECT * FROM transactions
        WHERE type='debit' AND countInStats=1 AND strftime('%Y-%m', date) = :monthKey
        ORDER BY amount DESC
    """)
    suspend fun getDebitTransactionsForMonth(monthKey: String): List<TransactionEntity>

    @Query("""
        SELECT date, SUM(amount) AS total
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND strftime('%Y-%m', date) = :monthKey
        GROUP BY date ORDER BY total DESC LIMIT 1
    """)
    suspend fun getMostExpensiveDayInMonth(monthKey: String): DayTotalRow?

    @Query("""
        SELECT CAST(SUBSTR(time,1,2) AS INTEGER) AS hour, COUNT(*) AS count, SUM(amount) AS totalAmount
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND strftime('%Y-%m', date) = :monthKey
        GROUP BY hour ORDER BY hour ASC
    """)
    suspend fun getTimeDistributionForMonth(monthKey: String): List<TimeDistRow>

    // --- Weekly summary screen queries ---

    /** Daily totals for each date in the week range (for bar chart). */
    @Query("""
        SELECT date, COALESCE(SUM(amount), 0) AS total
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date <= :endDate
        GROUP BY date ORDER BY date ASC
    """)
    suspend fun getDailyTotalsForRange(startDate: String, endDate: String): List<DayTotalRow>

    /** Top category by spend in a date range. */
    @Query("""
        SELECT LOWER(category) AS category, SUM(amount) AS amount
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date <= :endDate
        GROUP BY LOWER(category) ORDER BY amount DESC LIMIT 1
    """)
    suspend fun getTopCategoryInRange(startDate: String, endDate: String): CategoryStatRow?

    /** All categories by spend in a date range. */
    @Query("""
        SELECT LOWER(category) AS category, SUM(amount) AS amount
        FROM transactions
        WHERE type='debit' AND (countInStats=1 OR LOWER(category)='investment') AND date >= :startDate AND date <= :endDate
        GROUP BY LOWER(category) ORDER BY amount DESC
    """)
    suspend fun getAllCategoryStatsInRange(startDate: String, endDate: String): List<CategoryStatRow>

    /** Total uncategorized spend (category = 'Unknown' or 'others') in a date range. */
    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date <= :endDate
          AND (LOWER(TRIM(category)) = 'unknown' OR LOWER(TRIM(category)) = 'others')
    """)
    suspend fun getUncategorizedSpendInRange(startDate: String, endDate: String): Double

    /** Distinct transaction count in a date range. */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date <= :endDate
    """)
    suspend fun getTxCountInRange(startDate: String, endDate: String): Int

    /** Distinct merchant count in a date range. */
    @Query("""
        SELECT COUNT(DISTINCT merchant)
        FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date <= :endDate
    """)
    suspend fun getDistinctMerchantCountInRange(startDate: String, endDate: String): Int

    /** Category spend for current month (used by budget monitor) */
    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE type='debit' AND countInStats=1
          AND LOWER(category) = LOWER(:category)
          AND strftime('%Y-%m', date) = :monthKey
    """)
    suspend fun getCategorySpendForMonth(category: String, monthKey: String): Double

    /** Up to [limit] uncategorized transactions (category = 'Unknown' or 'others') in a date range, newest first. */
    @Query("""
        SELECT * FROM transactions
        WHERE type='debit' AND countInStats=1 AND date >= :startDate AND date <= :endDate
          AND (LOWER(TRIM(category)) = 'unknown' OR LOWER(TRIM(category)) = 'others')
        ORDER BY date DESC, time DESC
        LIMIT :limit
    """)
    suspend fun getUncategorizedTransactionsInRange(startDate: String, endDate: String, limit: Int): List<TransactionEntity>

    // --- Dynamic pattern reprocessing ---

    @Query("SELECT * FROM transactions WHERE needs_reprocess = 1 AND originalSms IS NOT NULL")
    suspend fun listNeedingReprocess(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE originalSms IS NOT NULL")
    suspend fun listAllWithOriginalSms(): List<TransactionEntity>

    @Query("""
        UPDATE transactions
        SET merchant = :merchant, category = :category, type = :type, accounts = :accounts, needs_reprocess = 0
        WHERE id = :id
    """)
    suspend fun updateReprocessedFields(id: String, merchant: String, category: String, type: String, accounts: String)

    @Query("UPDATE transactions SET needs_reprocess = 0 WHERE id = :id")
    suspend fun clearReprocessFlag(id: String)
}

data class AccountTotalsRow(
    val accounts: String,
    val monthTotal: Double,
    val allTimeTotal: Double
)

data class MonthlyStatsRow(val income: Double, val expense: Double)
data class CategoryStatRow(val category: String, val amount: Double)
data class MerchantStatRow(val merchant: String, val amount: Double, val transactionCount: Int)
data class MonthTotalRow(val monthKey: String, val total: Double)
data class RepeatedMerchantRow(val merchant: String, val transactionCount: Int, val totalAmount: Double)
data class RecurringDataRow(val merchant: String, val amount: Double, val month: String, val txDate: String?)
data class CountAmountRow(val count: Int, val totalAmount: Double?)
data class TimeDistRow(val hour: Int, val count: Int, val totalAmount: Double)
data class MerchantAmountRow(val merchant: String, val amount: Double)
data class DateRangeRow(val minDate: String?, val maxDate: String?, val count: Int)

data class DayTotalRow(val date: String, val total: Double)
data class DailySummaryRow(val totalSpend: Double, val txCount: Int)
