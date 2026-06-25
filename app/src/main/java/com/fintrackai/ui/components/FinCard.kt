package com.fintrackai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.Spacing

@Composable
fun FinCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AppShape.large,
    tonalElevation: Dp = 0.dp,
    contentPadding: Dp = Spacing.lg,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = tonalElevation
        ) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = tonalElevation
        ) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
