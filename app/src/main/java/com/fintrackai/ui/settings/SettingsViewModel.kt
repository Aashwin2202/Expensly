package com.fintrackai.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.data.local.preferences.PreferencesManager
import com.fintrackai.data.repository.TransactionRepository
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.domain.model.DateRange
import com.fintrackai.notification.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val dateRange: DateRange = DateRange("N/A", "N/A", 0),
    val customCategories: List<CustomCategory> = emptyList(),
    val rescanning: Boolean = false,
    val rescanProgress: String = "",
    val rescanPercent: Int = 0,
    val clearing: Boolean = false,
    val exportingCsv: Boolean = false,
    val importingCsv: Boolean = false,
    val transactionCsvMessage: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val prefs: PreferencesManager,
    private val analytics: AnalyticsHelper,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var rescanJob: Job? = null

    init {
        viewModelScope.launch {
            val theme = prefs.themeMode.first()
            val range = repo.getDateRange()
            _uiState.value = _uiState.value.copy(themeMode = theme, dateRange = range)
        }
        repo.getCustomCategoriesFlow()
            .onEach { cats -> _uiState.value = _uiState.value.copy(customCategories = cats) }
            .launchIn(viewModelScope)
    }

    fun saveCustomCategory(name: String, emoji: String, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val id = repo.addCustomCategoryFromInput(name, emoji) ?: return@launch
            onSaved(id)
        }
    }

    fun deleteCustomCategory(id: String) {
        viewModelScope.launch { repo.deleteCustomCategoryAndRecategorize(id) }
    }

    fun editCustomCategory(id: String, name: String, emoji: String) {
        viewModelScope.launch { repo.editCustomCategory(id, name, emoji) }
    }

    fun setThemeMode(mode: String) {
        analytics.logThemeChanged(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun onNotificationPermissionGranted() {
        notificationScheduler.reschedule()
    }

    fun clearTransactionCsvMessage() {
        _uiState.value = _uiState.value.copy(transactionCsvMessage = "")
    }

    fun exportTransactionsToUri(contentResolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        if (_uiState.value.exportingCsv || _uiState.value.importingCsv) return
        _uiState.value = _uiState.value.copy(exportingCsv = true, transactionCsvMessage = "")
        viewModelScope.launch {
            try {
                val (count, bytes) = repo.exportTransactionsCsvPayload()
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Could not open file")
                analytics.logCsvExported(count)
                val msg = if (count == 0) {
                    SettingsConstants.EXPORT_TRANSACTIONS_EMPTY
                } else {
                    SettingsConstants.EXPORT_TRANSACTIONS_SUCCESS.format(count)
                }
                _uiState.value = _uiState.value.copy(exportingCsv = false, transactionCsvMessage = msg)
            } catch (e: Exception) {
                Log.e("SettingsVM", "CSV export failed", e)
                analytics.logCsvExportFailed(e.message ?: "unknown")
                _uiState.value = _uiState.value.copy(
                    exportingCsv = false,
                    transactionCsvMessage = SettingsConstants.EXPORT_TRANSACTIONS_FAILED.format(e.message ?: "")
                )
            }
        }
    }

    fun importTransactionsFromUri(contentResolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        if (_uiState.value.exportingCsv || _uiState.value.importingCsv) return
        _uiState.value = _uiState.value.copy(importingCsv = true, transactionCsvMessage = "")
        viewModelScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read file")
                val result = repo.importTransactionsFromCsv(bytes)
                if (result.invalidHeader) {
                    analytics.logCsvImportFailed("invalid_header")
                } else {
                    analytics.logCsvImported(result.rowsInserted, result.rowsSkippedMalformed)
                }
                val msg = if (result.invalidHeader) {
                    SettingsConstants.IMPORT_TRANSACTIONS_BAD_FILE
                } else {
                    SettingsConstants.IMPORT_TRANSACTIONS_SUCCESS.format(result.rowsInserted, result.rowsSkippedMalformed)
                }
                _uiState.value = _uiState.value.copy(
                    importingCsv = false,
                    transactionCsvMessage = msg,
                    dateRange = repo.getDateRange()
                )
            } catch (e: Exception) {
                Log.e("SettingsVM", "CSV import failed", e)
                analytics.logCsvImportFailed(e.message ?: "unknown")
                _uiState.value = _uiState.value.copy(
                    importingCsv = false,
                    transactionCsvMessage = SettingsConstants.IMPORT_TRANSACTIONS_FAILED.format(e.message ?: "")
                )
            }
        }
    }

    fun suggestedExportFileName(): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "${com.fintrackai.domain.transactions.TransactionExportImportConstants.EXPORT_FILE_NAME_PREFIX}$day" +
            com.fintrackai.domain.transactions.TransactionExportImportConstants.EXPORT_FILE_SUFFIX
    }

    fun rescanSms(contentResolver: ContentResolver) {
        if (_uiState.value.rescanning) return
        analytics.logSmsRescanStarted()
        _uiState.value = _uiState.value.copy(rescanning = true, rescanPercent = 0, rescanProgress = "Reading SMS...")
        rescanJob = viewModelScope.launch {
            try {
                val range = repo.getDateRange()
                val since = startOfLocalDayMillisOrNull(range.maxDate)
                _uiState.value = _uiState.value.copy(rescanProgress = "Scanning SMS...")
                // Scanning SMS takes up to 90%; saving to DB takes the remaining 90→99%
                val (count, saved) = repo.scanSmsInboxAndSave(
                    contentResolver,
                    sinceMillisInclusive = since,
                    onProgress = { scanned, total ->
                        val rawPct = if (total > 0) (scanned * 100) / total else 100
                        // Cap at 90 — DB save happens after this phase
                        val pct = (rawPct * 90) / 100
                        _uiState.value = _uiState.value.copy(rescanPercent = pct)
                    }
                )
                // Slowly tick 90 → 99 while DB finalises (visual only, ~600 ms total)
                val currentPct = _uiState.value.rescanPercent
                for (p in (currentPct + 1)..99) {
                    _uiState.value = _uiState.value.copy(rescanPercent = p)
                    kotlinx.coroutines.delay(60L)
                }
                analytics.logSmsRescanCompleted(count, saved)
                prefs.setSmsPermissionGranted()
                val suffix = if (since == null) " (full inbox)" else " since ${range.maxDate}"
                _uiState.value = _uiState.value.copy(
                    rescanning = false,
                    rescanPercent = 100,
                    rescanProgress = "Rescan: added $saved new transactions from $count messages$suffix",
                    dateRange = repo.getDateRange()
                )
            } catch (e: Exception) {
                Log.e("SettingsVM", "SMS rescan failed", e)
                if (e is kotlinx.coroutines.CancellationException) {
                    analytics.logSmsRescanCancelled()
                } else {
                    analytics.logSmsRescanFailed(e.message ?: "unknown")
                }
                _uiState.value = _uiState.value.copy(
                    rescanning = false,
                    rescanPercent = 0,
                    rescanProgress = if (e is kotlinx.coroutines.CancellationException) "Rescan cancelled" else "Rescan failed: ${e.message}"
                )
            }
        }
    }

    fun cancelRescan() {
        rescanJob?.cancel()
        rescanJob = null
    }

    fun clearAllData() {
        analytics.logDataCleared()
        _uiState.value = _uiState.value.copy(clearing = true)
        viewModelScope.launch {
            repo.clearAllData()
            prefs.resetComputedState()
            _uiState.value = _uiState.value.copy(clearing = false, dateRange = DateRange("N/A", "N/A", 0))
        }
    }

}
