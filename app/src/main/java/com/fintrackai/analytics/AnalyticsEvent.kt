package com.fintrackai.analytics

/** All Firebase Analytics event names used across the app. */
object AnalyticsEvent {

    // ── Auth & Onboarding ─────────────────────────────────────────────────
    const val LOGIN_SUCCESS = "login_success"
    const val LOGIN_FAILED = "login_failed"
    const val LOGOUT = "logout"
    const val SMS_IMPORT_STARTED = "sms_import_started"
    const val SMS_IMPORT_COMPLETED = "sms_import_completed"
    const val SMS_IMPORT_FAILED = "sms_import_failed"
    const val SMS_IMPORT_SKIPPED = "sms_import_skipped"
    const val SMS_IMPORT_RETRIED = "sms_import_retried"
    const val SMS_PERMISSION_GRANTED = "sms_permission_granted"
    const val NOTIFICATION_PERMISSION_GRANTED = "notification_permission_granted"
    const val NOTIFICATION_PERMISSION_DENIED = "notification_permission_denied"

    // ── Transactions ──────────────────────────────────────────────────────
    const val TRANSACTION_ADDED_MANUAL = "transaction_added_manual"
    const val TRANSACTION_DELETED = "transaction_deleted"
    const val TRANSACTION_BULK_DELETED = "transaction_bulk_deleted"
    const val TRANSACTION_CATEGORY_CHANGED = "transaction_category_changed"
    const val TRANSACTION_MERCHANT_RENAMED = "transaction_merchant_renamed"
    const val TRANSACTION_MERGED = "transaction_merged"
    const val TRANSACTION_UNMERGED = "transaction_unmerged"
    const val TRANSACTION_LINK_STARTED = "transaction_link_started"
    const val TRANSACTION_LINK_COMPLETED = "transaction_link_completed"
    const val TRANSACTION_LINK_CANCELLED = "transaction_link_cancelled"
    const val TRANSACTION_WRONG_DETECTION_REPORTED = "transaction_wrong_detection_reported"
    const val TRANSACTION_FILTER_APPLIED = "transaction_filter_applied"
    const val TRANSACTION_SORT_CHANGED = "transaction_sort_changed"
    const val TRANSACTION_SEARCH_USED = "transaction_search_used"
    const val TRANSACTION_TAB_CHANGED = "transaction_tab_changed"

    // ── SMS Auto-Detection ────────────────────────────────────────────────
    const val SMS_TRANSACTION_DETECTED = "sms_transaction_detected"
    const val SMS_RESCAN_STARTED = "sms_rescan_started"
    const val SMS_RESCAN_COMPLETED = "sms_rescan_completed"
    const val SMS_RESCAN_FAILED = "sms_rescan_failed"
    const val SMS_RESCAN_CANCELLED = "sms_rescan_cancelled"
    const val UNDETECTED_SMS_VIEWED = "undetected_sms_viewed"
    const val UNDETECTED_SMS_SUBMITTED = "undetected_sms_submitted"

    // ── Budget ────────────────────────────────────────────────────────────
    const val BUDGET_SET_MONTHLY = "budget_set_monthly"
    const val BUDGET_REMOVED_MONTHLY = "budget_removed_monthly"
    const val BUDGET_SET_CATEGORY = "budget_set_category"
    const val BUDGET_REMOVED_CATEGORY = "budget_removed_category"

    // ── Reminders ─────────────────────────────────────────────────────────
    const val REMINDER_CREATED = "reminder_created"
    const val REMINDER_UPDATED = "reminder_updated"
    const val REMINDER_DELETED = "reminder_deleted"
    const val REMINDER_MARKED_PAID = "reminder_marked_paid"
    const val REMINDER_RECURRING_DISMISSED = "reminder_recurring_dismissed"

    // ── Notifications ─────────────────────────────────────────────────────
    const val NOTIFICATION_TAPPED = "notification_tapped"

    // ── Insights ──────────────────────────────────────────────────────────
    const val INSIGHT_VIEWED = "insight_viewed"
    const val INSIGHT_SAVED = "insight_saved"
    const val INSIGHT_UNSAVED = "insight_unsaved"
    const val INSIGHT_LOAD_FAILED = "insight_load_failed"
    const val INSIGHT_NOTIFICATION_PROMPT_SHOWN = "insight_notification_prompt_shown"
    const val INSIGHT_NOTIFICATION_PROMPT_DISMISSED = "insight_notification_prompt_dismissed"

    // ── Weekly Summary ────────────────────────────────────────────────────
    const val WEEKLY_INSIGHT_CARD_CLICKED = "weekly_insight_card_clicked"

    // ── Expense Wrapped ───────────────────────────────────────────────────
    const val WRAPPED_OPENED = "wrapped_opened"
    const val WRAPPED_NO_DATA = "wrapped_no_data"
    const val WRAPPED_LOAD_FAILED = "wrapped_load_failed"

    // ── Accounts ──────────────────────────────────────────────────────────
    const val ACCOUNT_CARD_TYPE_CHANGED = "account_card_type_changed"
    const val ACCOUNT_CARD_DELETED = "account_card_deleted"

    // ── Categories ────────────────────────────────────────────────────────
    const val CUSTOM_CATEGORY_CREATED = "custom_category_created"
    const val CUSTOM_CATEGORY_EDITED = "custom_category_edited"
    const val CUSTOM_CATEGORY_DELETED = "custom_category_deleted"

    // ── Settings ──────────────────────────────────────────────────────────
    const val THEME_CHANGED = "theme_changed"
    const val CSV_EXPORTED = "csv_exported"
    const val CSV_EXPORT_FAILED = "csv_export_failed"
    const val CSV_IMPORTED = "csv_imported"
    const val CSV_IMPORT_FAILED = "csv_import_failed"
    const val DATA_CLEARED = "data_cleared"

    // ── Errors ────────────────────────────────────────────────────────────
    const val ERROR_OCCURRED = "error_occurred"
}

/** Parameter key names shared across events. */
object AnalyticsParam {
    const val METHOD = "method"
    const val SUCCESS = "success"
    const val ERROR_MESSAGE = "error_message"
    const val ERROR_SOURCE = "error_source"
    const val MESSAGE_COUNT = "message_count"
    const val TRANSACTION_COUNT = "transaction_count"
    const val SAVED_COUNT = "saved_count"
    const val DURATION_MS = "duration_ms"
    const val CARD_TYPE = "card_type"
    const val CATEGORY = "category"
    const val FROM_CATEGORY = "from_category"
    const val TO_CATEGORY = "to_category"
    const val APPLIED_TO_ALL = "applied_to_all"
    const val INSIGHT_TYPE = "insight_type"
    const val FILTER_TYPE = "filter_type"
    const val SORT_BY = "sort_by"
    const val TAB = "tab"
    const val THEME = "theme"
    const val REMINDER_TYPE = "reminder_type"
    const val REMINDER_FREQUENCY = "reminder_frequency"
    const val FROM_TYPE = "from_type"
    const val TO_TYPE = "to_type"
    const val SENDER_COUNT = "sender_count"
    const val BUDGET_AMOUNT_BUCKET = "budget_amount_bucket"
    const val AMOUNT_BUCKET = "amount_bucket"
    const val NOTIFICATION_TYPE = "notification_type"
}
