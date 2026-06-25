package com.fintrackai.ui.settings

object SettingsConstants {
    const val RESCAN_MESSAGES_LABEL = "Rescan messages"
    const val SMS_PERMISSIONS_INCOMPLETE =
        "Read SMS should be allowed for imports and background detection."
    const val OPEN_APP_PERMISSION_SETTINGS = "Open app permission settings"

    const val EXPORT_TRANSACTIONS_LABEL = "Export transactions (CSV)"
    const val IMPORT_TRANSACTIONS_LABEL = "Import transactions (CSV)"
    const val EXPORT_TRANSACTIONS_HINT =
        "Saves all transactions as a CSV file you can open in a spreadsheet. Use the same format to import on this or another device."
    const val EXPORT_TRANSACTIONS_SUCCESS = "Exported %d transactions."
    const val EXPORT_TRANSACTIONS_EMPTY = "Nothing to export."
    const val EXPORT_TRANSACTIONS_FAILED = "Export failed: %s"
    const val IMPORT_TRANSACTIONS_SUCCESS =
        "Imported %1\$d new rows. Skipped %2\$d (invalid, duplicate, or already present)."
    const val IMPORT_TRANSACTIONS_FAILED = "Import failed: %s"
    const val IMPORT_TRANSACTIONS_BAD_FILE = "Could not read file or invalid CSV header."
}
