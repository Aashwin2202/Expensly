package com.fintrackai.domain.sms

import android.telephony.SmsMessage as PlatformSms
import com.fintrackai.domain.model.SmsMessage
import java.util.Calendar

/**
 * One [SMS_RECEIVED] intent can carry several PDUs for a single logical multipart SMS.
 * Each part must be concatenated in order; otherwise the parser may see fragments as separate
 * transactions and dedupe (sender + body) will not match.
 */
object SmsIncomingPduMergeHelper {

    fun mergedDomainMessages(parts: Array<PlatformSms>): List<SmsMessage> {
        if (parts.isEmpty()) return emptyList()
        val fullBody = buildString {
            for (p in parts) {
                val seg = p.displayMessageBody ?: p.messageBody
                if (!seg.isNullOrEmpty()) append(seg)
            }
        }
        if (fullBody.isBlank()) return emptyList()
        val first = parts[0]
        val address = (first.originatingAddress ?: first.displayOriginatingAddress)?.ifBlank { null } ?: ""
        val ts = parts.maxOf { it.timestampMillis }
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val date = String.format(
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val time = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        return listOf(SmsMessage(body = fullBody, timestamp = ts, address = address, date = date, time = time))
    }
}
