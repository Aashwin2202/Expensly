package com.fintrackai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing

@Composable
fun HeaderTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = (-0.5).sp
            ),
            color = ext.text
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ext.textSecondary
            )
        }
    }
}
