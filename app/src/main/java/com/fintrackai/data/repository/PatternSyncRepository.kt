package com.fintrackai.data.repository

import android.util.Log
import com.fintrackai.data.local.db.SmsPatternDao
import com.fintrackai.data.local.db.SmsPatternEntity
import com.fintrackai.data.remote.RemoteSmsPattern
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PatternSync"

sealed class SyncResult {
    data class Updated(val count: Int) : SyncResult()
    data object AlreadyCurrent : SyncResult()
    data class Failed(val error: Exception) : SyncResult()
}

@Singleton
class PatternSyncRepository @Inject constructor(
    private val smsPatternDao: SmsPatternDao,
    private val supabase: SupabaseClient
) {

    suspend fun syncPatterns(): SyncResult {
        if (supabase.auth.currentSessionOrNull() == null) {
            Log.d(TAG, "Pattern sync skipped: no authenticated session")
            return SyncResult.Failed(IllegalStateException("Not authenticated"))
        }
        return try {
            val remotePatterns = supabase
                .from("sms_patterns")
                .select {
                    order("priority", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<RemoteSmsPattern>()

            if (remotePatterns.isEmpty()) {
                Log.d(TAG, "No active patterns from remote")
                return SyncResult.AlreadyCurrent
            }

            val localMaxVersion = smsPatternDao.getMaxVersion() ?: 0
            val remoteMaxVersion = remotePatterns.maxOf { it.version }

            if (remoteMaxVersion <= localMaxVersion && smsPatternDao.count() == remotePatterns.size) {
                Log.d(TAG, "Patterns already up-to-date (version=$localMaxVersion)")
                return SyncResult.AlreadyCurrent
            }

            val entities = remotePatterns.map { it.toEntity() }
            smsPatternDao.upsertAll(entities)
            val activeIds = remotePatterns.map { it.id }
            smsPatternDao.deleteNotIn(activeIds)

            Log.d(TAG, "Synced ${entities.size} patterns (version=$remoteMaxVersion)")
            SyncResult.Updated(entities.size)
        } catch (e: Exception) {
            Log.w(TAG, "Pattern sync failed, using local cache", e)
            SyncResult.Failed(e)
        }
    }

    suspend fun getActivePatterns(): List<SmsPatternEntity> {
        return smsPatternDao.getAllOrdered()
    }
}
