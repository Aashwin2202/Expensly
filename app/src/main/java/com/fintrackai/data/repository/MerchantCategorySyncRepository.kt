package com.fintrackai.data.repository

import android.util.Log
import com.fintrackai.data.local.db.RemoteMerchantCategoryDao
import com.fintrackai.data.local.db.RemoteMerchantCategoryEntity
import com.fintrackai.data.remote.RemoteMerchantCategoryMapping
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MerchantCategorySync"

@Singleton
class MerchantCategorySyncRepository @Inject constructor(
    private val dao: RemoteMerchantCategoryDao,
    private val supabase: SupabaseClient
) {

    /** In-memory cache built from DB, keyed by keyword (lowercase). */
    @Volatile
    private var merchantCache: Map<String, String>? = null

    @Volatile
    private var wordCache: Map<String, String>? = null

    suspend fun syncMerchantCategories(): SyncResult {
        if (supabase.auth.currentSessionOrNull() == null) {
            Log.d(TAG, "Merchant category sync skipped: no authenticated session")
            return SyncResult.Failed(IllegalStateException("Not authenticated"))
        }
        return try {
            val remoteMappings = supabase
                .from("merchant_category_keywords")
                .select()
                .decodeList<RemoteMerchantCategoryMapping>()

            if (remoteMappings.isEmpty()) {
                Log.d(TAG, "No merchant category mappings from remote")
                return SyncResult.AlreadyCurrent
            }

            val localMaxVersion = dao.getMaxVersion() ?: 0
            val remoteMaxVersion = remoteMappings.maxOf { it.version }

            if (remoteMaxVersion <= localMaxVersion && dao.count() == remoteMappings.size) {
                Log.d(TAG, "Merchant categories already up-to-date (version=$localMaxVersion)")
                return SyncResult.AlreadyCurrent
            }

            val entities = remoteMappings.map { it.toEntity() }
            dao.upsertAll(entities)
            val activeIds = remoteMappings.map { it.id }
            dao.deleteNotIn(activeIds)

            // Invalidate in-memory cache so next access rebuilds from DB
            merchantCache = null
            wordCache = null

            Log.d(TAG, "Synced ${entities.size} merchant category mappings (version=$remoteMaxVersion)")
            SyncResult.Updated(entities.size)
        } catch (e: Exception) {
            Log.w(TAG, "Merchant category sync failed, using local cache", e)
            SyncResult.Failed(e)
        }
    }

    /**
     * Returns cached merchant→category map. Loads from DB on first call.
     * Sorted by keyword length descending (longest match first).
     */
    suspend fun getMerchantCategories(): Map<String, String> {
        merchantCache?.let { return it }
        val map = dao.getMerchantMappings()
            .associate { it.keyword.lowercase().trim() to it.category.lowercase().trim() }
        merchantCache = map
        return map
    }

    /**
     * Returns cached word→category map. Loads from DB on first call.
     * Sorted by keyword length descending (longest match first).
     */
    suspend fun getWordCategories(): Map<String, String> {
        wordCache?.let { return it }
        val map = dao.getWordMappings()
            .associate { it.keyword.lowercase().trim() to it.category.lowercase().trim() }
        wordCache = map
        return map
    }
}
