package com.fintrackai.ui.undetected

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrackai.analytics.AnalyticsHelper
import com.fintrackai.domain.model.SmsMessage
import com.fintrackai.domain.sms.SmsConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Calendar
import javax.inject.Inject

data class SenderGroup(
    val displayName: String,
    // All raw sender IDs that map to this display name (e.g. JD-HDFCBK-S, VM-HDFCBK-T)
    val senderIds: Set<String>,
    val latestSnippet: String,
    val messageCount: Int,
    val latestTimestamp: Long,
    val messages: List<SmsMessage>
)

data class UndetectedSmsUiState(
    val senderGroups: List<SenderGroup> = emptyList(),
    val allSenderGroups: List<SenderGroup> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedSenderMessages: List<SmsMessage> = emptyList(),
    val selectedDisplayName: String = "",
    val submittedIds: Set<String> = emptySet(),
    val submitting: Set<String> = emptySet()
)

// Ordered list: checked in order, first match wins.
// Each entry: canonical display name → list of prefixes the root must start with.
private val KNOWN_BANK_PREFIXES: List<Pair<String, List<String>>> = listOf(
    "HDFC"      to listOf("HDFC"),
    "ICICI"     to listOf("ICICI"),
    "SBI"       to listOf("SBI"),
    "AXIS"      to listOf("AXIS"),
    "KOTAK"     to listOf("KOTAK"),
    "INDUSIND"  to listOf("INDUSIND", "INDUS"),
    "YESBANK"   to listOf("YESBANK", "YESBK", "YES"),
    "IDFC"      to listOf("IDFC"),
    "PNB"       to listOf("PNB", "PUNJAB"),
    "RBL"       to listOf("RBL"),
    "DBS"       to listOf("DBS"),
    "HSBC"      to listOf("HSBC"),
    "CITI"      to listOf("CITI"),
    "BOB"       to listOf("BOB", "BANKOFB"),
    "BOI"       to listOf("BOI", "BANKOFI"),
    "CANARA"    to listOf("CANARA", "CANBNK"),
    "UNION"     to listOf("UNION"),
    "FEDERAL"   to listOf("FEDERAL", "FEDBK"),
    "BANDHAN"   to listOf("BANDHAN"),
    "UCO"       to listOf("UCO"),
    "IOB"       to listOf("IOB"),
    "CENTRAL"   to listOf("CENTRAL"),
    "INDIAN"    to listOf("INDIAN"),
    "PAYTM"     to listOf("PAYTM"),
    "PHONEPE"   to listOf("PHONEPE"),
    "GPAY"      to listOf("GPAY"),
    "AMAZON"    to listOf("AMAZON"),
    "AIRTEL"    to listOf("AIRTEL"),
    "JIO"       to listOf("JIO"),
)

@HiltViewModel
class UndetectedSmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val analytics: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(UndetectedSmsUiState())
    val uiState: StateFlow<UndetectedSmsUiState> = _uiState.asStateFlow()

    fun loadSms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val messages = withContext(Dispatchers.IO) {
                readAllInboxSms(context.contentResolver)
            }
            // Group by display name so JD-HDFCBK-S and VM-HDFCBK-T both go into "HDFC"
            val grouped = messages
                .groupBy { extractDisplayName(it.address) }
                .map { (displayName, msgs) ->
                    val sorted = msgs.sortedByDescending { it.timestamp }
                    SenderGroup(
                        displayName = displayName,
                        senderIds = msgs.map { it.address }.toSet(),
                        latestSnippet = sorted.first().body.take(80).replace('\n', ' '),
                        messageCount = msgs.size,
                        latestTimestamp = sorted.first().timestamp,
                        messages = sorted
                    )
                }
                .sortedByDescending { it.latestTimestamp }
            analytics.logUndetectedSmsViewed(grouped.size)
            _uiState.value = _uiState.value.copy(
                allSenderGroups = grouped,
                senderGroups = grouped,
                isLoading = false
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        val all = _uiState.value.allSenderGroups
        val filtered = if (query.isBlank()) {
            all
        } else {
            val q = query.trim().lowercase()
            all.filter { group ->
                group.displayName.lowercase().contains(q) ||
                    group.senderIds.any { it.lowercase().contains(q) } ||
                    group.messages.any { it.body.lowercase().contains(q) }
            }
        }
        _uiState.value = _uiState.value.copy(searchQuery = query, senderGroups = filtered)
    }

    fun selectSender(displayName: String) {
        val group = _uiState.value.allSenderGroups.firstOrNull { it.displayName == displayName }
        val query = _uiState.value.searchQuery.trim().lowercase()
        val messages = group?.messages ?: emptyList()
        val filtered = if (query.isBlank()) messages
                       else messages.filter { it.body.lowercase().contains(query) }
        _uiState.value = _uiState.value.copy(
            selectedDisplayName = displayName,
            selectedSenderMessages = filtered
        )
    }

    fun submitSms(sms: SmsMessage) {
        val key = "${sms.address}:${sms.timestamp}"
        if (key in _uiState.value.submittedIds || key in _uiState.value.submitting) return
        _uiState.value = _uiState.value.copy(submitting = _uiState.value.submitting + key)
        viewModelScope.launch {
            try {
                val payload = JsonObject(
                    mapOf(
                        "sender_id" to JsonPrimitive(sms.address),
                        "message_body" to JsonPrimitive(sms.body),
                        "sms_timestamp" to JsonPrimitive(sms.timestamp),
                        "submitted_at" to JsonPrimitive(System.currentTimeMillis())
                    )
                )
                supabase.from("undetected_sms").insert(payload)
                analytics.logUndetectedSmsSubmitted()
                _uiState.value = _uiState.value.copy(
                    submittedIds = _uiState.value.submittedIds + key,
                    submitting = _uiState.value.submitting - key
                )
            } catch (e: Exception) {
                Log.e("UndetectedSmsVM", "Failed to submit SMS", e)
                analytics.logError("undetected_sms_submit", e.message ?: "unknown")
                _uiState.value = _uiState.value.copy(submitting = _uiState.value.submitting - key)
            }
        }
    }

    private fun readAllInboxSms(contentResolver: ContentResolver): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(
            uri,
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC"
        ) ?: return messages
        cursor.use {
            val addrIdx = it.getColumnIndex("address")
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")
            while (it.moveToNext()) {
                val address = it.getString(addrIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val timestamp = it.getLong(dateIdx)
                val bodyLower = body.lowercase()
                val isOtp = SmsConstants.OTP_KEYWORDS.any { kw -> bodyLower.contains(kw) }
                if (isOtp) continue
                val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                val date = String.format(
                    "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                val time = String.format(
                    "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE)
                )
                messages.add(SmsMessage(body = body, timestamp = timestamp, address = address, date = date, time = time))
            }
        }
        return messages
    }

    /**
     * Maps any raw SMS sender ID to a canonical brand/bank name.
     *
     * Examples:
     *   JD-HDFCBK-S  → HDFC
     *   VM-HDFCBK-T  → HDFC
     *   TX-SBIBNK-X  → SBI
     *   AM-SBICRD-N  → SBI
     *   ICICIBK       → ICICI
     *   +919876543210 → +919876543210
     */
    fun extractDisplayName(senderId: String): String {
        if (senderId.startsWith("+") || senderId.all { it.isDigit() || it == '-' && !it.isLetter() }) {
            if (senderId.startsWith("+") || senderId.all { it.isDigit() }) return senderId
        }

        // Strip carrier prefix (XX-) and suffix (-X or -XX)
        val root = run {
            val parts = senderId.split("-")
            when {
                parts.size >= 3 && parts.first().length <= 3 && parts.last().length <= 2 ->
                    parts.subList(1, parts.size - 1).joinToString("")
                parts.size == 2 && parts.first().length <= 3 ->
                    parts[1]
                else -> senderId
            }
        }.uppercase()

        // Match against known bank/brand prefixes
        for ((canonicalName, prefixes) in KNOWN_BANK_PREFIXES) {
            if (prefixes.any { root.startsWith(it) }) return canonicalName
        }

        // Fallback: strip common noise suffixes and return what's left
        val noiseSuffixes = listOf("BANK", "BNK", "BK", "CRD", "INB", "NET", "PAY", "FIN", "CARD", "CORP", "LTD")
        for (suffix in noiseSuffixes) {
            if (root.endsWith(suffix) && root.length > suffix.length) {
                return root.removeSuffix(suffix)
            }
        }
        return root
    }
}
