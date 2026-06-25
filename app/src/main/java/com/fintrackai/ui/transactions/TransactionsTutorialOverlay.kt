package com.fintrackai.ui.transactions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.Spacing

@Composable
fun TransactionsTutorialOverlay(onDone: () -> Unit) {
    val scrimAlpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(300), label = "scrim")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDone)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.68f * scrimAlpha))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppShape.large),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    Text("Long press any transaction", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "To delete or merge a credit and debit pair",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
