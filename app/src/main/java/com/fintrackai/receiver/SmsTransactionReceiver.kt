package com.fintrackai.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.telephony.SmsMessage as PlatformSms
import com.fintrackai.domain.model.SmsMessage
import com.fintrackai.domain.sms.SmsIncomingPduMergeHelper
import com.fintrackai.work.ProcessIncomingSmsWorker

/**
 * Receives [Telephony.Sms.Intents.SMS_RECEIVED_ACTION] while the app is not running and
 * enqueues [ProcessIncomingSmsWorker] to parse and save bank transaction SMS.
 * Requires [Manifest.permission.RECEIVE_SMS] at runtime.
 */
class SmsTransactionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "RECEIVE_SMS not granted; skipping background SMS handling")
            return
        }
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.filterIsInstance<PlatformSms>()
            ?.toTypedArray()
            ?: return
        if (parts.isEmpty()) return

        if (parts.size > 1) {
            Log.d(TAG, "Multipart SMS: merging ${parts.size} PDU(s) into one logical message")
        }

        val domainMessages = SmsIncomingPduMergeHelper.mergedDomainMessages(parts)
        if (domainMessages.isEmpty()) return

        val data = bundleMessagesForWorker(domainMessages)
        val work = OneTimeWorkRequestBuilder<ProcessIncomingSmsWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(work)
    }

    companion object {
        private const val TAG = "SmsTransactionReceiver"

        internal fun bundleMessagesForWorker(messages: List<SmsMessage>): Data {
            val b = Data.Builder()
            b.putInt("count", messages.size)
            messages.forEachIndexed { i, m ->
                b.putString("body_$i", m.body)
                b.putString("addr_$i", m.address)
                b.putLong("ts_$i", m.timestamp)
                m.date?.let { b.putString("date_$i", it) }
                m.time?.let { b.putString("time_$i", it) }
            }
            return b.build()
        }
    }
}
