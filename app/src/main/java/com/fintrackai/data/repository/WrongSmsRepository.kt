package com.fintrackai.data.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WrongSmsRepository"

@Singleton
class WrongSmsRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    suspend fun report(
        rawSms: String,
        smsSender: String?,
        reason: String,
        comments: String?,
        detectedMerchant: String? = null,
        detectedAmount: Double? = null,
        detectedType: String? = null,
        detectedCategory: String? = null,
        detectedDate: String? = null,
        detectedTime: String? = null,
        detectedAccounts: String? = null,
        detectedReference: String? = null
    ): Result<Unit> {
        return try {
            val payload = JsonObject(
                mapOf(
                    "raw_sms" to JsonPrimitive(rawSms),
                    "sms_sender" to (if (smsSender != null) JsonPrimitive(smsSender) else JsonNull),
                    "reason" to JsonPrimitive(reason),
                    "comments" to (if (!comments.isNullOrBlank()) JsonPrimitive(comments) else JsonNull),
                    "detected_merchant" to (if (!detectedMerchant.isNullOrBlank()) JsonPrimitive(detectedMerchant) else JsonNull),
                    "detected_amount" to (if (detectedAmount != null) JsonPrimitive(detectedAmount) else JsonNull),
                    "detected_type" to (if (!detectedType.isNullOrBlank()) JsonPrimitive(detectedType) else JsonNull),
                    "detected_category" to (if (!detectedCategory.isNullOrBlank()) JsonPrimitive(detectedCategory) else JsonNull),
                    "detected_date" to (if (!detectedDate.isNullOrBlank()) JsonPrimitive(detectedDate) else JsonNull),
                    "detected_time" to (if (!detectedTime.isNullOrBlank()) JsonPrimitive(detectedTime) else JsonNull),
                    "detected_accounts" to (if (!detectedAccounts.isNullOrBlank()) JsonPrimitive(detectedAccounts) else JsonNull),
                    "detected_reference" to (if (!detectedReference.isNullOrBlank()) JsonPrimitive(detectedReference) else JsonNull)
                )
            )
            supabase.from("wrong_sms").insert(payload)
            Log.d(TAG, "Reported wrong SMS (reason=$reason)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to report wrong SMS", e)
            Result.failure(e)
        }
    }
}
