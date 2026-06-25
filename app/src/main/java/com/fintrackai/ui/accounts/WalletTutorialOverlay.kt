package com.fintrackai.ui.accounts

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

private const val SPOTLIGHT_PADDING_DP = 8f
private const val SPOTLIGHT_CORNER_DP = 20f

@Composable
fun WalletTutorialOverlay(
    creditCardBounds: Rect?,
    onDone: () -> Unit
) {
    val scrimAlpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(300), label = "scrim")

    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.systemBars.getTop(density).toFloat()

    val spotlightPadPx = with(density) { SPOTLIGHT_PADDING_DP.dp.toPx() }
    val spotlightCornerPx = with(density) { SPOTLIGHT_CORNER_DP.dp.toPx() }

    val spotlightRect = creditCardBounds?.let {
        Rect(
            left = it.left - spotlightPadPx,
            top = it.top - statusBarTopPx - spotlightPadPx,
            right = it.right + spotlightPadPx,
            bottom = it.bottom - statusBarTopPx + spotlightPadPx
        )
    }

    var overlayHeightPx by remember { mutableFloatStateOf(0f) }

    val tipCardHeightPx = with(density) { 140.dp.toPx() }
    val gapPx = with(density) { 16.dp.toPx() }
    val cardTopPx: Int = if (spotlightRect != null) {
        val cardBelow = overlayHeightPx == 0f || (spotlightRect.bottom + tipCardHeightPx + gapPx < overlayHeightPx)
        if (cardBelow)
            (spotlightRect.bottom + gapPx).roundToInt()
        else
            (spotlightRect.top - tipCardHeightPx - gapPx).roundToInt()
    } else {
        with(density) { (overlayHeightPx / 2 - 70.dp.toPx()).roundToInt() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlayHeightPx = it.height.toFloat() }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDone)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.68f * scrimAlpha))
            if (spotlightRect != null) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(spotlightRect.left, spotlightRect.top),
                    size = Size(spotlightRect.width, spotlightRect.height),
                    cornerRadius = CornerRadius(spotlightCornerPx),
                    blendMode = BlendMode.Clear
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, cardTopPx) },
                shape = RoundedCornerShape(AppShape.large),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    Text("Manage your cards", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Tap on card to see its expenses.\nLong press to delete or change its type", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        text = "Tap anywhere to dismiss",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}
