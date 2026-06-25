package com.fintrackai.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTH_PHONE = stringPreferencesKey("auth_phone")
        val AUTH_USER_NAME = stringPreferencesKey("auth_user_name")
        val AUTH_USER_EMAIL = stringPreferencesKey("auth_user_email")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        /** True after post-login SMS import finishes (or user skips). Cleared on logout. */
        val LOGIN_SMS_IMPORT_COMPLETED = booleanPreferencesKey("login_sms_import_completed")

        // Notification preferences
        val NOTIF_DAILY_REVIEW = booleanPreferencesKey("notif_daily_review")
        val NOTIF_WEEKLY_SUMMARY = booleanPreferencesKey("notif_weekly_summary")
        val NOTIF_BUDGET_ALERTS = booleanPreferencesKey("notif_budget_alerts")
        val NOTIF_REMINDERS = booleanPreferencesKey("notif_reminders")
        /** ISO date of the last daily review notification sent (e.g. "2026-04-04") */
        val NOTIF_LAST_DAILY_DATE = stringPreferencesKey("notif_last_daily_date")
        /** ISO date (Sunday) of the last weekly summary sent */
        val NOTIF_LAST_WEEKLY_DATE = stringPreferencesKey("notif_last_weekly_date")
        /** Count of non-budget notifications sent today */
        val NOTIF_TODAY_COUNT = intPreferencesKey("notif_today_count")
        /** Date string for the count above */
        val NOTIF_TODAY_COUNT_DATE = stringPreferencesKey("notif_today_count_date")
        /** Comma-separated "category:threshold" pairs already alerted this month */
        val NOTIF_BUDGET_ALERTS_SENT = stringPreferencesKey("notif_budget_alerts_sent")
        /** Month key for budget alerts sent tracker */
        val NOTIF_BUDGET_ALERTS_MONTH = stringPreferencesKey("notif_budget_alerts_month")
        /** Last "YYYY-MM" month key for which Expense Wrapped was shown */
        val WRAPPED_LAST_SHOWN_MONTH = stringPreferencesKey("wrapped_last_shown_month")
        /** Last "YYYY-MM" month key for which recurring transactions were computed */
        val RECURRING_LAST_COMPUTED_MONTH = stringPreferencesKey("recurring_last_computed_month")
        /** True after the user has completed the first-launch onboarding flow */
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        /** True after the user has seen the home screen feature tutorial */
        val HOME_TUTORIAL_SEEN = booleanPreferencesKey("home_tutorial_seen")
        /** True after the user has seen the wallet screen feature tutorial */
        val WALLET_TUTORIAL_SEEN = booleanPreferencesKey("wallet_tutorial_seen")
        /** True after the user has seen the transactions screen feature tutorial */
        val TX_TUTORIAL_SEEN = booleanPreferencesKey("tx_tutorial_seen")
        /** True after the user has seen the category picker long-press tip */
        val CATEGORY_TIP_SEEN = booleanPreferencesKey("category_tip_seen")
        /** True after the user has seen the transaction detail 3-dots tip */
        val TX_DETAIL_TIP_SEEN = booleanPreferencesKey("tx_detail_tip_seen")
        /** True once SMS permission has been granted (initial import or rescan) */
        val SMS_PERMISSION_GRANTED = booleanPreferencesKey("sms_permission_granted")
        /** True after the all-tabs-visited feedback prompt has been shown */
        val FEEDBACK_PROMPT_SHOWN = booleanPreferencesKey("feedback_prompt_shown")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_LOGGED_IN] ?: false }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        context.dataStore.edit { it[Keys.IS_LOGGED_IN] = loggedIn }
    }

    val authPhone: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_PHONE] }

    suspend fun setAuthPhone(phone: String?) {
        context.dataStore.edit {
            if (phone != null) it[Keys.AUTH_PHONE] = phone
            else it.remove(Keys.AUTH_PHONE)
        }
    }

    val authUserName: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_USER_NAME] }

    suspend fun setAuthUserName(name: String?) {
        context.dataStore.edit {
            if (name != null) it[Keys.AUTH_USER_NAME] = name
            else it.remove(Keys.AUTH_USER_NAME)
        }
    }

    val authUserEmail: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_USER_EMAIL] }

    suspend fun setAuthUserEmail(email: String?) {
        context.dataStore.edit {
            if (email != null) it[Keys.AUTH_USER_EMAIL] = email
            else it.remove(Keys.AUTH_USER_EMAIL)
        }
    }

    val loginSmsImportCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.LOGIN_SMS_IMPORT_COMPLETED] ?: false }

    suspend fun setLoginSmsImportCompleted(done: Boolean) {
        context.dataStore.edit { it[Keys.LOGIN_SMS_IMPORT_COMPLETED] = done }
    }

    // --- Notification preferences ---

    val notifDailyReview: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIF_DAILY_REVIEW] ?: true }

    suspend fun setNotifDailyReview(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIF_DAILY_REVIEW] = enabled }
    }

    val notifWeeklySummary: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIF_WEEKLY_SUMMARY] ?: true }

    suspend fun setNotifWeeklySummary(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIF_WEEKLY_SUMMARY] = enabled }
    }

    val notifBudgetAlerts: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIF_BUDGET_ALERTS] ?: true }

    suspend fun setNotifBudgetAlerts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIF_BUDGET_ALERTS] = enabled }
    }

    val notifReminders: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIF_REMINDERS] ?: true }

    suspend fun setNotifReminders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIF_REMINDERS] = enabled }
    }

    suspend fun getNotifLastDailyDate(): String? =
        context.dataStore.data.map { it[Keys.NOTIF_LAST_DAILY_DATE] }.first()

    suspend fun setNotifLastDailyDate(date: String) {
        context.dataStore.edit { it[Keys.NOTIF_LAST_DAILY_DATE] = date }
    }

    suspend fun getNotifLastWeeklyDate(): String? =
        context.dataStore.data.map { it[Keys.NOTIF_LAST_WEEKLY_DATE] }.first()

    suspend fun setNotifLastWeeklyDate(date: String) {
        context.dataStore.edit { it[Keys.NOTIF_LAST_WEEKLY_DATE] = date }
    }

    /** Returns true if we can send a non-budget notification (max 2/day). Increments the counter. */
    suspend fun tryIncrementNotifCount(): Boolean {
        val today = java.time.LocalDate.now().toString()
        var allowed = false
        context.dataStore.edit { prefs ->
            val storedDate = prefs[Keys.NOTIF_TODAY_COUNT_DATE] ?: ""
            val count = if (storedDate == today) (prefs[Keys.NOTIF_TODAY_COUNT] ?: 0) else 0
            if (count < 2) {
                prefs[Keys.NOTIF_TODAY_COUNT] = count + 1
                prefs[Keys.NOTIF_TODAY_COUNT_DATE] = today
                allowed = true
            }
        }
        return allowed
    }

    /** Check if a budget alert (category + threshold like "food:80") was already sent this month. */
    suspend fun wasBudgetAlertSent(category: String, threshold: Int): Boolean {
        val monthKey = java.time.LocalDate.now().let { "${it.year}-${it.monthValue.toString().padStart(2, '0')}" }
        val key = "${category.lowercase()}:$threshold"
        val prefs = context.dataStore.data.first()
        val storedMonth = prefs[Keys.NOTIF_BUDGET_ALERTS_MONTH] ?: ""
        if (storedMonth != monthKey) return false
        val sent = prefs[Keys.NOTIF_BUDGET_ALERTS_SENT] ?: ""
        return key in sent.split(",")
    }

    /** Mark a budget alert as sent for this month. */
    suspend fun markBudgetAlertSent(category: String, threshold: Int) {
        val monthKey = java.time.LocalDate.now().let { "${it.year}-${it.monthValue.toString().padStart(2, '0')}" }
        val key = "${category.lowercase()}:$threshold"
        context.dataStore.edit { prefs ->
            val storedMonth = prefs[Keys.NOTIF_BUDGET_ALERTS_MONTH] ?: ""
            if (storedMonth != monthKey) {
                // New month — reset
                prefs[Keys.NOTIF_BUDGET_ALERTS_MONTH] = monthKey
                prefs[Keys.NOTIF_BUDGET_ALERTS_SENT] = key
            } else {
                val existing = prefs[Keys.NOTIF_BUDGET_ALERTS_SENT] ?: ""
                if (key !in existing.split(",")) {
                    prefs[Keys.NOTIF_BUDGET_ALERTS_SENT] = if (existing.isEmpty()) key else "$existing,$key"
                }
            }
        }
    }

    val wrappedLastShownMonth: Flow<String?> =
        context.dataStore.data.map { it[Keys.WRAPPED_LAST_SHOWN_MONTH] }

    suspend fun setWrappedLastShownMonth(monthKey: String) {
        context.dataStore.edit { it[Keys.WRAPPED_LAST_SHOWN_MONTH] = monthKey }
    }

    suspend fun clearWrappedLastShownMonth() {
        context.dataStore.edit { it.remove(Keys.WRAPPED_LAST_SHOWN_MONTH) }
    }

    val onboardingCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
    }

    suspend fun clearOnboardingCompleted() {
        context.dataStore.edit { it.remove(Keys.ONBOARDING_COMPLETED) }
    }

    val homeTutorialSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HOME_TUTORIAL_SEEN] ?: false }

    suspend fun setHomeTutorialSeen() {
        context.dataStore.edit { it[Keys.HOME_TUTORIAL_SEEN] = true }
    }

    val walletTutorialSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.WALLET_TUTORIAL_SEEN] ?: false }

    suspend fun setWalletTutorialSeen() {
        context.dataStore.edit { it[Keys.WALLET_TUTORIAL_SEEN] = true }
    }

    val txTutorialSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.TX_TUTORIAL_SEEN] ?: false }

    suspend fun setTxTutorialSeen() {
        context.dataStore.edit { it[Keys.TX_TUTORIAL_SEEN] = true }
    }

    val categoryTipSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CATEGORY_TIP_SEEN] ?: false }

    suspend fun setCategoryTipSeen() {
        context.dataStore.edit { it[Keys.CATEGORY_TIP_SEEN] = true }
    }

    val txDetailTipSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.TX_DETAIL_TIP_SEEN] ?: false }

    suspend fun setTxDetailTipSeen() {
        context.dataStore.edit { it[Keys.TX_DETAIL_TIP_SEEN] = true }
    }

    val feedbackPromptShown: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.FEEDBACK_PROMPT_SHOWN] ?: false }

    suspend fun setFeedbackPromptShown() {
        context.dataStore.edit { it[Keys.FEEDBACK_PROMPT_SHOWN] = true }
    }

    val smsPermissionGranted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SMS_PERMISSION_GRANTED] ?: false }

    suspend fun setSmsPermissionGranted() {
        context.dataStore.edit { it[Keys.SMS_PERMISSION_GRANTED] = true }
    }

    suspend fun clearTutorialsSeen() {
        context.dataStore.edit {
            it.remove(Keys.HOME_TUTORIAL_SEEN)
            it.remove(Keys.WALLET_TUTORIAL_SEEN)
            it.remove(Keys.TX_TUTORIAL_SEEN)
            it.remove(Keys.CATEGORY_TIP_SEEN)
            it.remove(Keys.TX_DETAIL_TIP_SEEN)
        }
    }

    suspend fun getRecurringLastComputedMonth(): String? =
        context.dataStore.data.map { it[Keys.RECURRING_LAST_COMPUTED_MONTH] }.first()

    suspend fun setRecurringLastComputedMonth(monthKey: String) {
        context.dataStore.edit { it[Keys.RECURRING_LAST_COMPUTED_MONTH] = monthKey }
    }

    suspend fun resetComputedState() {
        context.dataStore.edit {
            it.remove(Keys.RECURRING_LAST_COMPUTED_MONTH)
            it.remove(Keys.WRAPPED_LAST_SHOWN_MONTH)
            it.remove(Keys.NOTIF_LAST_DAILY_DATE)
            it.remove(Keys.NOTIF_LAST_WEEKLY_DATE)
            it.remove(Keys.NOTIF_TODAY_COUNT)
            it.remove(Keys.NOTIF_TODAY_COUNT_DATE)
            it.remove(Keys.NOTIF_BUDGET_ALERTS_SENT)
            it.remove(Keys.NOTIF_BUDGET_ALERTS_MONTH)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
