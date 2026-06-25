package com.fintrackai

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import com.fintrackai.BuildConfig
import androidx.work.Configuration
import com.fintrackai.notification.NotificationManagerHelper
import com.fintrackai.notification.NotificationScheduler
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinTrackApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationManagerHelper: NotificationManagerHelper
    @Inject lateinit var notificationScheduler: NotificationScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Enable Crashlytics crash collection (disable in debug builds to reduce noise)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        // Register notification channels and schedule periodic workers.
        // Workers are always scheduled — they silently no-op if POST_NOTIFICATIONS is denied.
        // InsightsScreen prompts the user for permission so notifications actually arrive.
        notificationManagerHelper.createChannels()
        notificationScheduler.schedule()
    }
}
