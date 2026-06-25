package com.fintrackai.ui.auth

object PostLoginImportConstants {
    const val TITLE = "Setting up your finances"

    const val SUBTITLE_SCANNING = "Scanning your SMS for bank alerts…"
    const val PERMISSION_HINT =
        "Allow SMS access to import past transactions and detect new ones automatically."
    const val CONTINUE_WITHOUT = "Continue without importing"

    val TIPS = listOf(
        "Expensly learns from your SMS — no manual typing for every card swipe.",
        "Add categories to see where money actually goes.",
        "Tracking small spends adds up: ₹200/day is over ₹6,000 a month.",
        "Merchants with repeating charges are easier to spot once everything is in one place.",
        "Your data is stored locally on your device.",
        "Debit vs credit in one timeline makes month-end less stressful.",
        "Budgets work best when they’re fed by real alerts, not memory.",
        "Insights get sharper as more transactions land in the app.",
        "Seeing trends by month beats guessing if you’re spending more than last year.",
    )
}
