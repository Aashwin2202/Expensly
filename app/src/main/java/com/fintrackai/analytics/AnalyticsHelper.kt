package com.fintrackai.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsHelper @Inject constructor(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics
) {

    // ── Internal helpers ─────────────────────────────────────────────────

    private fun log(event: String, block: (Bundle.() -> Unit)? = null) {
        val bundle = if (block != null) Bundle().apply(block) else null
        analytics.logEvent(event, bundle)
    }

    private fun amountBucket(amount: Double): String = when {
        amount < 500 -> "0-500"
        amount < 2_000 -> "500-2000"
        amount < 10_000 -> "2000-10000"
        amount < 50_000 -> "10000-50000"
        else -> "50000+"
    }

    // ── Auth & Onboarding ────────────────────────────────────────────────

    fun logLoginSuccess() = log(AnalyticsEvent.LOGIN_SUCCESS) {
        putString(AnalyticsParam.METHOD, "google")
    }

    fun logLoginFailed(error: String, cause: Throwable? = null) {
        log(AnalyticsEvent.LOGIN_FAILED) {
            putString(AnalyticsParam.ERROR_MESSAGE, error.take(100))
        }
        if (cause == null || !isTransientNetworkError(cause)) {
            crashlytics.recordException(cause ?: RuntimeException("Login failed: $error"))
        }
    }

    private fun isTransientNetworkError(e: Throwable): Boolean {
        val name = e::class.qualifiedName ?: ""
        return e is java.io.IOException ||
            name.contains("Timeout", ignoreCase = true) ||
            name.contains("ConnectException", ignoreCase = true)
    }

    fun logLogout() = log(AnalyticsEvent.LOGOUT)

    fun logSmsImportStarted() = log(AnalyticsEvent.SMS_IMPORT_STARTED)

    fun logSmsImportCompleted(messageCount: Int, savedCount: Int, durationMs: Long) =
        log(AnalyticsEvent.SMS_IMPORT_COMPLETED) {
            putLong(AnalyticsParam.MESSAGE_COUNT, messageCount.toLong())
            putLong(AnalyticsParam.SAVED_COUNT, savedCount.toLong())
            putLong(AnalyticsParam.DURATION_MS, durationMs)
        }

    fun logSmsImportFailed(error: String) {
        log(AnalyticsEvent.SMS_IMPORT_FAILED) {
            putString(AnalyticsParam.ERROR_MESSAGE, error.take(100))
        }
        crashlytics.recordException(RuntimeException("SMS import failed: $error"))
    }

    fun logSmsImportSkipped() = log(AnalyticsEvent.SMS_IMPORT_SKIPPED)

    fun logSmsImportRetried() = log(AnalyticsEvent.SMS_IMPORT_RETRIED)

    fun logNotificationPermissionGranted() = log(AnalyticsEvent.NOTIFICATION_PERMISSION_GRANTED)

    fun logNotificationPermissionDenied() = log(AnalyticsEvent.NOTIFICATION_PERMISSION_DENIED)

    /**
     * Fired when the user taps a system notification and the app opens.
     * [notificationType] maps to the destination prefix (e.g. "daily_review", "weekly_summary",
     * "budget", "reminders", "transactions_date", "transactions_range").
     */
    fun logNotificationTapped(notificationType: String) = log(AnalyticsEvent.NOTIFICATION_TAPPED) {
        putString(AnalyticsParam.NOTIFICATION_TYPE, notificationType)
    }

    /** Fired when the user taps the weekly insight hero card on the Weekly Summary screen. */
    fun logWeeklyInsightCardClicked() = log(AnalyticsEvent.WEEKLY_INSIGHT_CARD_CLICKED)

    // ── Transactions ─────────────────────────────────────────────────────

    fun logTransactionAddedManual(category: String, amount: Double, type: String) =
        log(AnalyticsEvent.TRANSACTION_ADDED_MANUAL) {
            putString(AnalyticsParam.CATEGORY, category)
            putString(AnalyticsParam.AMOUNT_BUCKET, amountBucket(amount))
            putString(AnalyticsParam.CARD_TYPE, type) // "debit" / "credit"
        }

    fun logTransactionDeleted() = log(AnalyticsEvent.TRANSACTION_DELETED)

    fun logTransactionBulkDeleted(count: Int) = log(AnalyticsEvent.TRANSACTION_BULK_DELETED) {
        putLong(AnalyticsParam.TRANSACTION_COUNT, count.toLong())
    }

    fun logTransactionCategoryChanged(from: String, to: String, appliedToAll: Boolean) =
        log(AnalyticsEvent.TRANSACTION_CATEGORY_CHANGED) {
            putString(AnalyticsParam.FROM_CATEGORY, from)
            putString(AnalyticsParam.TO_CATEGORY, to)
            putString(AnalyticsParam.APPLIED_TO_ALL, appliedToAll.toString())
        }

    fun logTransactionMerchantRenamed(applyToAll: Boolean) =
        log(AnalyticsEvent.TRANSACTION_MERCHANT_RENAMED) {
            putString(AnalyticsParam.APPLIED_TO_ALL, applyToAll.toString())
        }

    fun logTransactionMerged() = log(AnalyticsEvent.TRANSACTION_MERGED)

    fun logTransactionUnmerged() = log(AnalyticsEvent.TRANSACTION_UNMERGED)

    fun logTransactionLinkStarted() = log(AnalyticsEvent.TRANSACTION_LINK_STARTED)

    fun logTransactionLinkCompleted() = log(AnalyticsEvent.TRANSACTION_LINK_COMPLETED)

    fun logTransactionLinkCancelled() = log(AnalyticsEvent.TRANSACTION_LINK_CANCELLED)

    fun logTransactionWrongDetectionReported() =
        log(AnalyticsEvent.TRANSACTION_WRONG_DETECTION_REPORTED)

    fun logTransactionFilterApplied(filterType: String?, hasCategory: Boolean, hasAmountRange: Boolean) =
        log(AnalyticsEvent.TRANSACTION_FILTER_APPLIED) {
            putString(AnalyticsParam.FILTER_TYPE, filterType ?: "none")
            putString("has_category_filter", hasCategory.toString())
            putString("has_amount_range", hasAmountRange.toString())
        }

    fun logTransactionSortChanged(sortBy: String) = log(AnalyticsEvent.TRANSACTION_SORT_CHANGED) {
        putString(AnalyticsParam.SORT_BY, sortBy)
    }

    fun logTransactionTabChanged(tab: Int) = log(AnalyticsEvent.TRANSACTION_TAB_CHANGED) {
        val tabName = when (tab) { 0 -> "transactions"; 1 -> "categories"; 2 -> "merchants"; else -> "unknown" }
        putString(AnalyticsParam.TAB, tabName)
    }

    fun logSmsRescanStarted() = log(AnalyticsEvent.SMS_RESCAN_STARTED)

    fun logSmsRescanCompleted(messageCount: Int, savedCount: Int) =
        log(AnalyticsEvent.SMS_RESCAN_COMPLETED) {
            putLong(AnalyticsParam.MESSAGE_COUNT, messageCount.toLong())
            putLong(AnalyticsParam.SAVED_COUNT, savedCount.toLong())
        }

    fun logSmsRescanFailed(error: String) {
        log(AnalyticsEvent.SMS_RESCAN_FAILED) {
            putString(AnalyticsParam.ERROR_MESSAGE, error.take(100))
        }
        crashlytics.recordException(RuntimeException("SMS rescan failed: $error"))
    }

    fun logSmsRescanCancelled() = log(AnalyticsEvent.SMS_RESCAN_CANCELLED)

    fun logUndetectedSmsViewed(senderCount: Int) = log(AnalyticsEvent.UNDETECTED_SMS_VIEWED) {
        putLong(AnalyticsParam.SENDER_COUNT, senderCount.toLong())
    }

    fun logUndetectedSmsSubmitted() = log(AnalyticsEvent.UNDETECTED_SMS_SUBMITTED)

    // ── Budget ───────────────────────────────────────────────────────────

    fun logBudgetSetMonthly(amount: Double) = log(AnalyticsEvent.BUDGET_SET_MONTHLY) {
        putString(AnalyticsParam.BUDGET_AMOUNT_BUCKET, amountBucket(amount))
    }

    fun logBudgetRemovedMonthly() = log(AnalyticsEvent.BUDGET_REMOVED_MONTHLY)

    fun logBudgetSetCategory(categoryId: String, amount: Double) =
        log(AnalyticsEvent.BUDGET_SET_CATEGORY) {
            putString(AnalyticsParam.CATEGORY, categoryId)
            putString(AnalyticsParam.BUDGET_AMOUNT_BUCKET, amountBucket(amount))
        }

    fun logBudgetRemovedCategory(categoryId: String) = log(AnalyticsEvent.BUDGET_REMOVED_CATEGORY) {
        putString(AnalyticsParam.CATEGORY, categoryId)
    }

    // ── Reminders ────────────────────────────────────────────────────────

    fun logReminderCreated(type: String, frequency: String) = log(AnalyticsEvent.REMINDER_CREATED) {
        putString(AnalyticsParam.REMINDER_TYPE, type)
        putString(AnalyticsParam.REMINDER_FREQUENCY, frequency)
    }

    fun logReminderUpdated(type: String) = log(AnalyticsEvent.REMINDER_UPDATED) {
        putString(AnalyticsParam.REMINDER_TYPE, type)
    }

    fun logReminderDeleted() = log(AnalyticsEvent.REMINDER_DELETED)

    fun logReminderMarkedPaid() = log(AnalyticsEvent.REMINDER_MARKED_PAID)

    fun logReminderRecurringDismissed() = log(AnalyticsEvent.REMINDER_RECURRING_DISMISSED)

    // ── Insights ─────────────────────────────────────────────────────────

    fun logInsightViewed(insightType: String) = log(AnalyticsEvent.INSIGHT_VIEWED) {
        putString(AnalyticsParam.INSIGHT_TYPE, insightType)
    }

    fun logInsightSaved(insightType: String) = log(AnalyticsEvent.INSIGHT_SAVED) {
        putString(AnalyticsParam.INSIGHT_TYPE, insightType)
    }

    fun logInsightUnsaved(insightType: String) = log(AnalyticsEvent.INSIGHT_UNSAVED) {
        putString(AnalyticsParam.INSIGHT_TYPE, insightType)
    }

    fun logInsightLoadFailed(insightType: String, error: String) {
        log(AnalyticsEvent.INSIGHT_LOAD_FAILED) {
            putString(AnalyticsParam.INSIGHT_TYPE, insightType)
            putString(AnalyticsParam.ERROR_MESSAGE, error.take(100))
        }
        crashlytics.recordException(RuntimeException("Insight load failed [$insightType]: $error"))
    }

    fun logInsightNotificationPromptShown() = log(AnalyticsEvent.INSIGHT_NOTIFICATION_PROMPT_SHOWN)

    fun logInsightNotificationPromptDismissed() = log(AnalyticsEvent.INSIGHT_NOTIFICATION_PROMPT_DISMISSED)

    // ── Expense Wrapped ──────────────────────────────────────────────────

    fun logWrappedOpened() = log(AnalyticsEvent.WRAPPED_OPENED)

    fun logWrappedNoData() = log(AnalyticsEvent.WRAPPED_NO_DATA)

    fun logWrappedLoadFailed(error: String) {
        log(AnalyticsEvent.WRAPPED_LOAD_FAILED) {
            putString(AnalyticsParam.ERROR_MESSAGE, error.take(100))
        }
        crashlytics.recordException(RuntimeException("Wrapped load failed: $error"))
    }

    // ── Accounts ─────────────────────────────────────────────────────────

    fun logAccountCardTypeChanged(from: String, to: String) = log(AnalyticsEvent.ACCOUNT_CARD_TYPE_CHANGED) {
        putString(AnalyticsParam.FROM_TYPE, from)
        putString(AnalyticsParam.TO_TYPE, to)
    }

    fun logAccountCardDeleted() = log(AnalyticsEvent.ACCOUNT_CARD_DELETED)

    // ── Categories ───────────────────────────────────────────────────────

    fun logCustomCategoryCreated() = log(AnalyticsEvent.CUSTOM_CATEGORY_CREATED)

    fun logCustomCategoryEdited() = log(AnalyticsEvent.CUSTOM_CATEGORY_EDITED)

    fun logCustomCategoryDeleted() = log(AnalyticsEvent.CUSTOM_CATEGORY_DELETED)

    // ── Settings ─────────────────────────────────────────────────────────

    fun logThemeChanged(theme: String) = log(AnalyticsEvent.THEME_CHANGED) {
        putString(AnalyticsParam.THEME, theme)
    }

    fun logCsvExported(transactionCount: Int) = log(AnalyticsEvent.CSV_EXPORTED) {
        putLong(AnalyticsParam.TRANSACTION_COUNT, transactionCount.toLong())
    }

    fun logCsvExportFailed(error: String) {
        log(AnalyticsEvent.CSV_EXPORT_FAILED) {
            putString(AnalyticsParam.ERROR_MESSAGE, error.take(100))
        }
        crashlytics.recordException(RuntimeException("CSV export failed: $error"))
    }

    fun logCsvImported(rowsInserted: Int, rowsSkipped: Int) = log(AnalyticsEvent.CSV_IMPORTED) {
        putLong("rows_inserted", rowsInserted.toLong())
        putLong("rows_skipped", rowsSkipped.toLong())
    }

    fun logCsvImportFailed(error: String) {
        log(AnalyticsEvent.CSV_IMPORT_FAILED) {
            putString(AnalyticsParam.ERROR_MESSAGE, error.take(100))
        }
        crashlytics.recordException(RuntimeException("CSV import failed: $error"))
    }

    fun logDataCleared() = log(AnalyticsEvent.DATA_CLEARED)

    // ── Generic error ────────────────────────────────────────────────────

    fun logError(source: String, error: String) {
        log(AnalyticsEvent.ERROR_OCCURRED) {
            putString(AnalyticsParam.ERROR_SOURCE, source)
            putString(AnalyticsParam.ERROR_MESSAGE, error.take(100))
        }
        crashlytics.recordException(RuntimeException("[$source] $error"))
    }
}
