package com.fintrackai.ui.auth

sealed interface GoogleSignInResult {
    data class Success(val idToken: String, val displayName: String?, val email: String?) : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
}
