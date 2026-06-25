package com.fintrackai.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.Spacing
import kotlin.math.roundToInt

private data class TutorialStep(val bounds: Rect, val title: String, val body: String)

private const val SPOTLIGHT_PADDING_DP = 8f
private const val SPOTLIGHT_CORNER_DP = 20f

@Composable
fun HomeTutorialOverlay(
    heroCardBounds: Rect?,
    firstTxBounds: Rect?,
    onDone: () -> Unit
) {
    if (heroCardBounds == null) return

    val steps = remember(heroCardBounds, firstTxBounds) {
        buildList {
            add(TutorialStep(heroCardBounds, "Filter by type", "Tap DEBIT or CREDIT to filter your transactions by type"))
            if (firstTxBounds != null) add(TutorialStep(firstTxBounds, "Change category", "Tap the category icon on any transaction to reassign it"))
        }
    }

    var stepIndex by remember { mutableIntStateOf(0) }
    val step = steps.getOrNull(stepIndex) ?: return

    val advance = { if (stepIndex < steps.lastIndex) stepIndex++ else onDone() }

    val scrimAlpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(300), label = "scrim")

    val density = LocalDensity.current

    // boundsInRoot() is in window coordinates (top = 0 includes status bar).
    // This overlay Box is inside the content area, so its own top = 0 is below the status bar.
    // Subtract the status bar inset from every Y to align spotlight with the overlay's coordinate space.
    val statusBarTopPx = WindowInsets.systemBars.getTop(density).toFloat()

    val spotlightPadPx = with(density) { SPOTLIGHT_PADDING_DP.dp.toPx() }
    val spotlightCornerPx = with(density) { SPOTLIGHT_CORNER_DP.dp.toPx() }

    val spotlightRect = Rect(
        left = step.bounds.left - spotlightPadPx,
        top = step.bounds.top - statusBarTopPx - spotlightPadPx,
        right = step.bounds.right + spotlightPadPx,
        bottom = step.bounds.bottom - statusBarTopPx + spotlightPadPx
    )

    // Measure the overlay's own height via onSizeChanged so tip card placement is accurate
    var overlayHeightPx by remember { mutableFloatStateOf(0f) }

    val tipCardHeightPx = with(density) { 160.dp.toPx() }
    val gapPx = with(density) { 16.dp.toPx() }
    val cardBelow = overlayHeightPx == 0f || (spotlightRect.bottom + tipCardHeightPx + gapPx < overlayHeightPx)
    val cardTopPx = if (cardBelow)
        spotlightRect.bottom + gapPx
    else
        spotlightRect.top - tipCardHeightPx - gapPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlayHeightPx = it.height.toFloat() }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { advance() }
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.68f * scrimAlpha))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(spotlightRect.left, spotlightRect.top),
                size = Size(spotlightRect.width, spotlightRect.height),
                cornerRadius = CornerRadius(spotlightCornerPx),
                blendMode = BlendMode.Clear
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, cardTopPx.roundToInt()) },
                shape = RoundedCornerShape(AppShape.large),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    Text(step.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(step.body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            steps.indices.forEach { i ->
                                Box(
                                    modifier = Modifier
                                        .size(if (i == stepIndex) 8.dp else 6.dp)
                                        .background(
                                            color = if (i == stepIndex) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                            }
                        }
                        Text(
                            text = if (stepIndex < steps.lastIndex) "Tap anywhere to continue" else "Tap anywhere to dismiss",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}
