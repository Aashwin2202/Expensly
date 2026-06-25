package com.fintrackai.ui.auth

object AuthConstants {
    const val CONTINUE_WITH_GOOGLE = "Continue with Google"
    const val MISSING_WEB_CLIENT_ID = "Add GOOGLE_WEB_CLIENT_ID to gradle.properties (Web client ID from Google Cloud)."
    const val GOOGLE_SIGN_IN_FAILED = "Google sign-in was cancelled or failed."
    const val GOOGLE_NO_ID_TOKEN =
        "Could not get an ID token from Google. Use the Web client ID (not Android-only) in gradle.properties."
    const val SUPABASE_SIGN_IN_FAILED = "Could not finish sign-in. Try again."
    const val RATE_LIMITED = "Too many sign-in attempts. Please wait a few minutes and try again."
}
