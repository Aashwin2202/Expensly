package com.fintrackai.ui.insights

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.notification.NotificationPermissionHelper
import com.fintrackai.ui.components.HeaderTitle
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val ext = LocalExtendedColors.current
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val granted = NotificationPermissionHelper.isGranted(context)
        viewModel.onScreenEntered(granted)
    }

    if (state.showNotificationPrompt) {
        NotificationPermissionDialog(
            onAllow = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(NotificationPermissionHelper.PERMISSION)
                } else {
                    viewModel.onNotificationPermissionResult(true)
                }
            },
            onDismiss = { viewModel.dismissNotificationPrompt() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.lg, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderTitle("Insights", "AI-powered spending analysis", modifier = Modifier.fillMaxWidth())

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🚀",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Coming Soon",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = ext.text,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "AI-powered spending insights\nare on their way!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ext.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    /*
    // --- INSIGHTS CARD UI (commented out, restore when feature is ready) ---

    val state by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { viewModel.insightItems.size })
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val granted = NotificationPermissionHelper.isGranted(context)
        viewModel.onScreenEntered(granted)
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentIndex(pagerState.currentPage)
    }

    LaunchedEffect(Unit) {
        viewModel.loadInsight(viewModel.insightItems[0].id)
    }

    if (state.showNotificationPrompt) {
        NotificationPermissionDialog(
            onAllow = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(NotificationPermissionHelper.PERMISSION)
                } else {
                    viewModel.onNotificationPermissionResult(true)
                }
            },
            onDismiss = { viewModel.dismissNotificationPrompt() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.lg, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderTitle("Insights", "AI-powered spending analysis", modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            val item = viewModel.insightItems[page]
            val isLoading = state.loading[item.id] == true
            val text = state.insights[item.id]
            val isSaved = state.savedInsights.contains(item.id)

            Surface(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = item.title,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        IconButton(onClick = { viewModel.toggleSaved(item.id) }) {
                            Icon(
                                if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                "Save",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(item.loadingText, color = ext.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        Text(
                            text = text ?: "No insight available yet.",
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            color = ext.text,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ext.border)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${page + 1} of ${viewModel.insightItems.size}",
                                fontSize = 12.sp,
                                color = ext.textSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(viewModel.insightItems.size) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                            else ext.border
                        )
                )
            }
        }
    }
    */
}

@Composable
private fun NotificationPermissionDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Stay on Top of Your Finances",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• Daily spending summaries every evening\n• Weekly comparisons with last week\n• Budget alerts before you overspend\n• Reminders of recurring payments",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onAllow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Enable Notifications",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Maybe Later",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
