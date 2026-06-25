package com.fintrackai.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fintrackai.data.local.db.TransactionDao
import com.fintrackai.data.local.preferences.PreferencesManager
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.model.SmsMessage
import com.fintrackai.notification.NotificationManagerHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@HiltWorker
class ProcessIncomingSmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationManagerHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val count = inputData.getInt("count", 0)
        if (count <= 0) return Result.success()
        val list = ArrayList<SmsMessage>(count)
        for (i in 0 until count) {
            val body = inputData.getString("body_$i") ?: continue
            val addr = inputData.getString("addr_$i") ?: ""
            val ts = inputData.getLong("ts_$i", 0L)
            val date = inputData.getString("date_$i")
            val time = inputData.getString("time_$i")
            list.add(SmsMessage(body = body, timestamp = ts, address = addr, date = date, time = time))
        }
        if (list.isEmpty()) return Result.success()
        val saved = repo.processIncomingSmsMessages(list)

        // If we saved new transactions and the daily notification was already sent today,
        // refresh it so it reflects the updated total rather than the stale snapshot.
        if (saved > 0) refreshDailyNotificationIfSentToday()

        return Result.success()
    }

    private suspend fun refreshDailyNotificationIfSentToday() {
        if (!preferencesManager.notifDailyReview.first()) return
        val today = LocalDate.now().toString()
        if (preferencesManager.getNotifLastDailyDate() != today) return

        val stats = transactionDao.getDailySummaryStats(today)
        if (stats.txCount == 0) return
        val topCategory = transactionDao.getTopCategoryForDate(today)?.category
        val merchantCount = transactionDao.getDistinctMerchantCountForDate(today)

        notificationHelper.sendDailyReview(
            date = today,
            totalSpend = stats.totalSpend,
            txCount = stats.txCount,
            topCategory = topCategory,
            merchantCount = merchantCount
        )
    }
}
