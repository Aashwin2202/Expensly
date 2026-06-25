package com.fintrackai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fintrackai.data.local.preferences.PreferencesManager
import com.fintrackai.navigation.AppNavHost
import com.fintrackai.notification.NotificationManagerHelper
import com.fintrackai.ui.theme.FinTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val notifDestination = intent.getStringExtra(NotificationManagerHelper.EXTRA_DESTINATION)
        setContent {
            val themeMode by preferencesManager.themeMode.collectAsState(initial = "system")
            FinTrackTheme(themeMode = themeMode) {
                AppNavHost(notifDestination = notifDestination)
            }
        }
    }
}
