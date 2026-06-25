package com.fintrackai.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fintrackai.ui.theme.AppShape
import com.fintrackai.ui.theme.Spacing

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    outlined: Boolean = false
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().height(52.dp),
            enabled = enabled && !loading,
            shape = RoundedCornerShape(AppShape.medium)
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(Spacing.xl), strokeWidth = 2.dp)
            else Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().height(52.dp),
            enabled = enabled && !loading,
            shape = RoundedCornerShape(AppShape.medium),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (loading) CircularProgressIndicator(
                modifier = Modifier.size(Spacing.xl),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            else Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(AppShape.medium),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
