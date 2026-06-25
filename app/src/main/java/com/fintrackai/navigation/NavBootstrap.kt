package com.fintrackai.navigation

data class NavBootstrap(
    val isLoggedIn: Boolean,
    val loginSmsImportCompleted: Boolean,
    val shouldShowWrapped: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val homeTutorialSeen: Boolean = false,
    val walletTutorialSeen: Boolean = false,
    val txTutorialSeen: Boolean = false,
    val categoryTipSeen: Boolean = false,
    val txDetailTipSeen: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val feedbackPromptShown: Boolean = false
)
