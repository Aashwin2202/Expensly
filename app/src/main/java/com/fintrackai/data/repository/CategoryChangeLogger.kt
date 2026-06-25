package com.fintrackai.data.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CategoryChangeLogger"

@Singleton
class CategoryChangeLogger @Inject constructor(
    private val supabase: SupabaseClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fire-and-forget: logs a user category change to Supabase.
     * Failures are silently logged — this must never block or crash the UI flow.
     */
    fun log(merchant: String, previousCategory: String, newCategory: String) {
        if (previousCategory == newCategory) return
        scope.launch {
            try {
                val params = JsonObject(
                    mapOf(
                        "p_merchant" to JsonPrimitive(merchant),
                        "p_previous_category" to JsonPrimitive(previousCategory),
                        "p_new_category" to JsonPrimitive(newCategory)
                    )
                )
                supabase.postgrest.rpc("upsert_category_change_log", params)
                Log.d(TAG, "Logged category change: $merchant ($previousCategory -> $newCategory)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to log category change", e)
            }
        }
    }
}
