package com.fintrackai.ui.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.local.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val userName: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val prefs: PreferencesManager,
    private val googleSignInHelper: GoogleSignInHelper,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        if (!googleSignInHelper.isConfigured()) {
            _uiState.value = _uiState.value.copy(error = AuthConstants.MISSING_WEB_CLIENT_ID)
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (!googleSignInHelper.isConfigured()) {
            _uiState.value = _uiState.value.copy(error = AuthConstants.MISSING_WEB_CLIENT_ID)
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = googleSignInHelper.signIn(activityContext)) {
                GoogleSignInResult.Cancelled -> {
                    _uiState.value = _uiState.value.copy(loading = false)
                }
                is GoogleSignInResult.Error -> {
                    analytics.logLoginFailed(result.message)
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = AuthConstants.GOOGLE_SIGN_IN_FAILED
                    )
                }
                is GoogleSignInResult.Success -> completeSupabaseSignIn(result)
            }
        }
    }

    private suspend fun completeSupabaseSignIn(result: GoogleSignInResult.Success) {
        val label = result.displayName?.takeIf { it.isNotBlank() } ?: result.email
        try {
            supabase.auth.signInWith(IDToken) {
                this.idToken = result.idToken
                provider = Google
            }
            prefs.setLoggedIn(true)
            prefs.setAuthPhone(null)
            if (!label.isNullOrBlank()) {
                prefs.setAuthUserName(label)
            }
            if (!result.email.isNullOrBlank()) {
                prefs.setAuthUserEmail(result.email)
            }
            analytics.logLoginSuccess()
            _uiState.value = _uiState.value.copy(
                loading = false,
                isLoggedIn = true,
                userName = label
            )
        } catch (e: Exception) {
            Log.e("AuthVM", "Supabase Google sign-in failed", e)
            analytics.logLoginFailed(e.message ?: AuthConstants.SUPABASE_SIGN_IN_FAILED, cause = e)
            val userMessage = if (isRateLimitError(e)) AuthConstants.RATE_LIMITED
                             else AuthConstants.SUPABASE_SIGN_IN_FAILED
            _uiState.value = _uiState.value.copy(loading = false, error = userMessage)
        }
    }

    private fun isRateLimitError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("rate limit") || msg.contains("429") || msg.contains("too many requests")
    }

    suspend fun logout() {
        analytics.logLogout()
        try {
            supabase.auth.signOut()
        } catch (_: Exception) {
        }
        googleSignInHelper.signOut()
        prefs.setLoggedIn(false)
        prefs.setAuthPhone(null)
        prefs.setAuthUserName(null)
        prefs.setAuthUserEmail(null)
        prefs.setLoginSmsImportCompleted(false)
        prefs.clearWrappedLastShownMonth()
        prefs.clearOnboardingCompleted()
        prefs.clearTutorialsSeen()
        val configError = if (googleSignInHelper.isConfigured()) null
                         else AuthConstants.MISSING_WEB_CLIENT_ID
        _uiState.value = AuthUiState(error = configError)
    }
}
