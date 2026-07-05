package com.fintrackai.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
// import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
// import com.fintrackai.domain.transactions.TransactionExportImportConstants
// import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.notification.NotificationPermissionHelper
import com.fintrackai.ui.components.CategoryPickerSheet
import com.fintrackai.ui.components.HeaderTitle
import com.fintrackai.ui.theme.LocalExtendedColors
import com.google.android.play.core.review.ReviewManagerFactory

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onNavigateToUndetectedSms: () -> Unit = {},
    onNavigateToWeeklySummary: () -> Unit = {},
    onNavigateToWrapped: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val ext = LocalExtendedColors.current

    var pendingSmsAction by remember { mutableStateOf<SettingsPendingSmsAction?>(null) }
    val smsPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val readOk = results[Manifest.permission.READ_SMS] == true
        val action = pendingSmsAction
        pendingSmsAction = null
        if (!readOk || action == null) return@rememberLauncherForActivityResult
        viewModel.rescanSms(context.contentResolver)
    }

    var notifPermissionGranted by remember {
        mutableStateOf(NotificationPermissionHelper.isGranted(context))
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notifPermissionGranted = true
            viewModel.onNotificationPermissionGranted()
        } else {
            // On second+ denial the OS may have suppressed the dialog; send to system settings
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var showManageCategories by remember { mutableStateOf(false) }

    // val exportCsvLauncher = rememberLauncherForActivityResult(
    //     CreateDocument(TransactionExportImportConstants.CSV_MIME_TYPE)
    // ) { uri ->
    //     viewModel.exportTransactionsToUri(context.contentResolver, uri)
    // }

    // val importCsvLauncher = rememberLauncherForActivityResult(
    //     ActivityResultContracts.OpenDocument()
    // ) { uri ->
    //     viewModel.importTransactionsFromUri(context.contentResolver, uri)
    // }

    // LaunchedEffect(state.transactionCsvMessage) {
    //     if (state.transactionCsvMessage.isNotEmpty()) {
    //         delay(5_000)
    //         viewModel.clearTransactionCsvMessage()
    //     }
    // }

    if (state.rescanning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Scanning messages") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { state.rescanPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        when {
                            state.rescanPercent < 90 -> "${state.rescanPercent}% scanned"
                            state.rescanPercent < 100 -> "${state.rescanPercent}% saving..."
                            else -> "Done"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRescan() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("This will permanently delete all transactions, categories, budgets, and reminders. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllData(); showClearDialog = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    if (showManageCategories) {
        CategoryPickerSheet(
            selectedCategory = "",
            customCategories = state.customCategories,
            onSaveCustomCategory = viewModel::saveCustomCategory,
            onDeleteCustomCategory = viewModel::deleteCustomCategory,
            onEditCustomCategory = viewModel::editCustomCategory,
            onSelect = {},
            onDismiss = { showManageCategories = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        HeaderTitle("Settings")
        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ext.text)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Theme", color = ext.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf("light" to "Light", "dark" to "Dark", "system" to "System")
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = state.themeMode == value,
                        onClick = { viewModel.setThemeMode(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            Text("Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ext.text)
            Spacer(modifier = Modifier.height(8.dp))
            Text("${state.dateRange.count} transactions", color = ext.textSecondary, style = MaterialTheme.typography.bodySmall)
            if (state.dateRange.minDate != "N/A") {
                Text("${state.dateRange.minDate} to ${state.dateRange.maxDate}", color = ext.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(12.dp))

            val smsBusy = state.rescanning
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.rescanSms(context.contentResolver)
                    } else {
                        pendingSmsAction = SettingsPendingSmsAction.Rescan
                        smsPermissionLauncher.launch(smsPermissions)
                    }
                },
                enabled = !smsBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.rescanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (state.rescanning) "Rescanning..."
                    else SettingsConstants.RESCAN_MESSAGES_LABEL
                )
            }
            if (state.rescanProgress.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.rescanProgress, color = ext.textSecondary, style = MaterialTheme.typography.bodySmall)
            }

            // Spacer(modifier = Modifier.height(20.dp))
            // Text("Export / import", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ext.text)
            // Spacer(modifier = Modifier.height(8.dp))
            // Text(
            //     SettingsConstants.EXPORT_TRANSACTIONS_HINT,
            //     color = ext.textSecondary,
            //     style = MaterialTheme.typography.bodySmall
            // )
            // Spacer(modifier = Modifier.height(10.dp))
            // OutlinedButton(
            //     onClick = { exportCsvLauncher.launch(viewModel.suggestedExportFileName()) },
            //     enabled = !smsBusy,
            //     modifier = Modifier.fillMaxWidth()
            // ) {
            //     if (state.exportingCsv) {
            //         CircularProgressIndicator(
            //             modifier = Modifier.size(20.dp),
            //             color = MaterialTheme.colorScheme.primary
            //         )
            //         Spacer(modifier = Modifier.width(8.dp))
            //     }
            //     Text(SettingsConstants.EXPORT_TRANSACTIONS_LABEL)
            // }
            // Spacer(modifier = Modifier.height(8.dp))
            // OutlinedButton(
            //     onClick = {
            //         importCsvLauncher.launch(
            //             arrayOf(
            //                 TransactionExportImportConstants.CSV_MIME_TYPE,
            //                 "text/comma-separated-values",
            //                 "*/*"
            //             )
            //         )
            //     },
            //     enabled = !smsBusy,
            //     modifier = Modifier.fillMaxWidth()
            // ) {
            //     if (state.importingCsv) {
            //         CircularProgressIndicator(
            //             modifier = Modifier.size(20.dp),
            //             color = MaterialTheme.colorScheme.primary
            //         )
            //         Spacer(modifier = Modifier.width(8.dp))
            //     }
            //     Text(SettingsConstants.IMPORT_TRANSACTIONS_LABEL)
            // }
            // if (state.transactionCsvMessage.isNotEmpty()) {
            //     Spacer(modifier = Modifier.height(8.dp))
            //     Text(state.transactionCsvMessage, color = ext.textSecondary, style = MaterialTheme.typography.bodySmall)
            // }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToUndetectedSms,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Report Undetected SMS")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showManageCategories = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Categories")
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToWrapped,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Expenses Wrapped")
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearDialog = true },
                enabled = !state.clearing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear All Data")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!notifPermissionGranted) {
            SettingsCard {
                Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ext.text)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enable notifications to get budget alerts, daily reviews, and reminders.",
                    color = ext.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // Pre-API-33: notifications controlled via app system settings
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Notifications")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            val activity = context as? androidx.activity.ComponentActivity
            Text("App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ext.text)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val openPlayStore = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        )
                    }
                    if (activity != null) {
                        val manager = ReviewManagerFactory.create(context)
                        manager.requestReviewFlow().addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                manager.launchReviewFlow(activity, task.result)
                                    .addOnCompleteListener { openPlayStore() }
                            } else {
                                openPlayStore()
                            }
                        }
                    } else {
                        openPlayStore()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rate on Play Store")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:")
                            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("support@expensly.co.in"))
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Expensly — Issue Report")
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Report an Issue")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Log Out")
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
