package com.fintrackai.ui.auth

import android.content.ContentResolver
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.local.preferences.PreferencesManager
import com.fintrackai.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostLoginImportUiState(
    val phase: PostLoginImportPhase = PostLoginImportPhase.Scanning,
    val messageCount: Int = 0,
    val savedCount: Int = 0,
    val skipped: Boolean = false,
    val error: String? = null
)

enum class PostLoginImportPhase {
    Scanning,
    Done,
    Failed
}

@HiltViewModel
class PostLoginImportViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val prefs: PreferencesManager,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostLoginImportUiState())
    val uiState: StateFlow<PostLoginImportUiState> = _uiState.asStateFlow()

    private var importJobStarted = false

    fun runFullInboxImport(contentResolver: ContentResolver) {
        if (importJobStarted) return
        importJobStarted = true
        val startMs = System.currentTimeMillis()
        analytics.logSmsImportStarted()
        viewModelScope.launch {
            _uiState.value = PostLoginImportUiState(phase = PostLoginImportPhase.Scanning, error = null)
            try {
                val (count, saved) = repo.scanSmsInboxAndSave(contentResolver, sinceMillisInclusive = null)
                val durationMs = System.currentTimeMillis() - startMs
                analytics.logSmsImportCompleted(count, saved, durationMs)
                _uiState.value = PostLoginImportUiState(
                    phase = PostLoginImportPhase.Done,
                    messageCount = count,
                    savedCount = saved,
                    skipped = false
                )
                prefs.setLoginSmsImportCompleted(true)
                prefs.setSmsPermissionGranted()
            } catch (e: Exception) {
                Log.e("PostLoginImport", "Import failed", e)
                analytics.logSmsImportFailed(e.message ?: "Import failed")
                importJobStarted = false
                _uiState.value = PostLoginImportUiState(
                    phase = PostLoginImportPhase.Failed,
                    error = e.message ?: "Import failed"
                )
            }
        }
    }

    fun skipImport() {
        analytics.logSmsImportSkipped()
        viewModelScope.launch {
            prefs.setLoginSmsImportCompleted(true)
            _uiState.value = PostLoginImportUiState(
                phase = PostLoginImportPhase.Done,
                messageCount = 0,
                savedCount = 0,
                skipped = true
            )
        }
    }

    fun retryImport(contentResolver: ContentResolver) {
        analytics.logSmsImportRetried()
        importJobStarted = false
        runFullInboxImport(contentResolver)
    }
}
