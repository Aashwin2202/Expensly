package com.fintrackai.ui.undetected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.domain.model.SmsMessage
import com.fintrackai.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderMessagesScreen(
    senderId: String,
    onBack: () -> Unit,
    viewModel: UndetectedSmsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current

    LaunchedEffect(senderId) {
        if (state.selectedDisplayName != senderId) {
            viewModel.selectSender(senderId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = senderId,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.selectedSenderMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.selectedSenderMessages, key = { "${it.address}:${it.timestamp}" }) { sms ->
                    SmsMessageCard(
                        sms = sms,
                        searchQuery = state.searchQuery,
                        isSubmitted = "${sms.address}:${sms.timestamp}" in state.submittedIds,
                        isSubmitting = "${sms.address}:${sms.timestamp}" in state.submitting,
                        onReport = { viewModel.submitSms(sms) }
                    )
                }
            }
        }
    }
}

@Composable
private fun highlightedText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val highlightColor = Color(0xFFFFEB3B).copy(alpha = 0.6f)
    return buildAnnotatedString {
        val lower = text.lowercase()
        val lowerQuery = query.trim().lowercase()
        var start = 0
        while (start < text.length) {
            val idx = lower.indexOf(lowerQuery, start)
            if (idx == -1) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(idx, idx + lowerQuery.length))
            }
            start = idx + lowerQuery.length
        }
    }
}

@Composable
private fun SmsMessageCard(
    sms: SmsMessage,
    searchQuery: String,
    isSubmitted: Boolean,
    isSubmitting: Boolean,
    onReport: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val timeStr = remember(sms.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(sms.timestamp))
    }
    val bodyText = highlightedText(text = sms.body, query = searchQuery)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sms.address,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodySmall,
                color = ext.text
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReport,
                enabled = !isSubmitted && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submitting...")
                } else if (isSubmitted) {
                    Text("Reported \u2713")
                } else {
                    Text("Report as Undetected")
                }
            }
        }
    }
}
