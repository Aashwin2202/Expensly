package com.fintrackai.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.ui.theme.LocalExtendedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PostLoginImportScreen(
    onContinueToHome: () -> Unit,
    viewModel: PostLoginImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val tips = PostLoginImportConstants.TIPS

    val readSmsGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsedSec++
        }
    }

    val tipIndex = remember(elapsedSec, tips.size) {
        PostLoginImportTipsHelper.tipIndexForElapsedSeconds(elapsedSec.toLong(), tips.size)
    }

    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(state.phase, readSmsGranted) {
        if (state.phase == PostLoginImportPhase.Scanning && readSmsGranted) {
            progressAnim.snapTo(0f)
            progressAnim.animateTo(
                PostLoginImportProgressHelper.FAST_TARGET,
                tween(
                    PostLoginImportProgressHelper.FAST_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    LaunchedEffect(state.phase, readSmsGranted) {
        if (state.phase != PostLoginImportPhase.Scanning || !readSmsGranted) return@LaunchedEffect
        while (isActive) {
            delay(PostLoginImportProgressHelper.SLOW_TICK_MS)
            if (progressAnim.value >= PostLoginImportProgressHelper.SLOW_CAP) continue
            val target = (progressAnim.value + PostLoginImportProgressHelper.SLOW_STEP)
                .coerceAtMost(PostLoginImportProgressHelper.SLOW_CAP)
            progressAnim.animateTo(
                target,
                tween(PostLoginImportProgressHelper.SLOW_ANIM_MS, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == PostLoginImportPhase.Done) {
            progressAnim.animateTo(
                1f,
                tween(PostLoginImportProgressHelper.DONE_DURATION_MS, easing = FastOutSlowInEasing)
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.READ_SMS] == true) {
            viewModel.runFullInboxImport(context.contentResolver)
        }
    }

    LaunchedEffect(readSmsGranted) {
        if (readSmsGranted) {
            viewModel.runFullInboxImport(context.contentResolver)
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == PostLoginImportPhase.Done) {
            delay(900)
            onContinueToHome()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                PostLoginImportConstants.TITLE,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                fontSize = 28.sp,
                color = ext.text,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                !readSmsGranted && state.phase == PostLoginImportPhase.Scanning -> {
                    Text(
                        PostLoginImportConstants.PERMISSION_HINT,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ext.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    TipCard(tipIndex = tipIndex, tips = tips)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Allow SMS access")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.skipImport() }) {
                        Text(PostLoginImportConstants.CONTINUE_WITHOUT)
                    }
                }

                state.phase == PostLoginImportPhase.Failed -> {
                    Text(
                        state.error ?: "Something went wrong",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.retryImport(context.contentResolver) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Try again")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.skipImport() }) {
                        Text("Continue to app")
                    }
                }

                else -> {
                    Text(
                        PostLoginImportConstants.SUBTITLE_SCANNING,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ext.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { progressAnim.value },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    TipCard(tipIndex = tipIndex, tips = tips)
                    if (state.phase == PostLoginImportPhase.Done) {
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.skipped) {
                            Text(
                                "You can import SMS anytime from Settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ext.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                "Imported ${state.savedCount} new transactions from ${state.messageCount} messages.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ext.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipCard(
    tipIndex: Int,
    tips: List<String>
) {
    val ext = LocalExtendedColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Crossfade(
            targetState = tipIndex,
            label = "tip"
        ) { idx ->
            Text(
                tips[idx],
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleMedium,
                color = ext.text,
                textAlign = TextAlign.Center
            )
        }
    }
}
