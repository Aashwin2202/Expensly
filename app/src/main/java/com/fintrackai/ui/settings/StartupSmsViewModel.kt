package com.fintrackai.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.data.repository.MerchantCategorySyncRepository
import com.fintrackai.data.repository.PatternSyncRepository
import com.fintrackai.data.repository.SyncResult
import com.fintrackai.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "StartupSms"

@HiltViewModel
class StartupSmsViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val patternSyncRepo: PatternSyncRepository,
    private val merchantCategorySyncRepo: MerchantCategorySyncRepository
) : ViewModel() {

    /**
     * Same logic as manual rescan (messages since newest saved txn date). No UI.
     * Skips if [Manifest.permission.READ_SMS] is not granted.
     */
    fun runLaunchRescanIfNeeded(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        viewModelScope.launch {
            try {
                // Sync SMS patterns and merchant categories from Supabase before rescan
                val syncResult = patternSyncRepo.syncPatterns()
                merchantCategorySyncRepo.syncMerchantCategories()

                // If new patterns arrived, re-run them against previously weak-matched transactions
                if (syncResult is SyncResult.Updated) {
                    val patterns = patternSyncRepo.getActivePatterns()
                    val reprocessResult = repo.reprocessUnmatchedTransactions(dynamicPatterns = patterns, fullScan = false)
                    Log.d(TAG, "Reprocess after sync: examined=${reprocessResult.examined} updated=${reprocessResult.updated}")
                }

                repo.backfillSmsDedupeHashes()
                val range = repo.getDateRange()
                if (!hasSavedTransactionsForSmsDateScope(range)) {
                    Log.d(TAG, "Launch rescan: skip (no saved transactions; scope is last txn date → now)")
                    return@launch
                }
                val since = startOfLocalDayMillisOrNull(range.maxDate)
                val (count, saved) = repo.scanSmsInboxAndSave(context.contentResolver, sinceMillisInclusive = since)
                Log.d(TAG, "Launch rescan: +$saved new transactions (scanned $count messages since ${range.maxDate})")
            } catch (e: Exception) {
                Log.e(TAG, "Launch rescan failed", e)
            }
        }
    }
}
