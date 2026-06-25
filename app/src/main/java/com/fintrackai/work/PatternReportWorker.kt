package com.fintrackai.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Background worker that reports an anonymised SMS skeleton to Supabase.
 *
 * Privacy guarantees:
 *   - Receives only skeleton text (all PII stripped by [SmsAnonymizer])
 *   - Receives only the sender-code (e.g. "AX-HDFCBK"), never the phone number
 *   - No user ID, device ID, or install ID is attached
 *   - Uses only the Supabase (authenticated, row-level safe)
 *
 * On transient network failure the work is retried up to [MAX_ATTEMPTS] times
 * via exponential back-off before being discarded.
 */
@HiltWorker
class PatternReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val supabase: SupabaseClient
) : CoroutineWorker(context, params) {

    companion object {

        const val KEY_SKELETON = "skeleton"
        const val KEY_HASH    = "hash"
        const val KEY_SENDER  = "sender"
        private const val MAX_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        val skeleton = inputData.getString(KEY_SKELETON) ?: return Result.failure()
        val hash     = inputData.getString(KEY_HASH)     ?: return Result.failure()
        val sender   = inputData.getString(KEY_SENDER)   ?: ""

        return try {
            supabase.postgrest.rpc(
                function   = "increment_pattern_hit",
                parameters = buildJsonObject {
                    put("p_hash",     hash)
                    put("p_skeleton", skeleton)
                    put("p_sender",   sender)
                }
            )
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }
}
