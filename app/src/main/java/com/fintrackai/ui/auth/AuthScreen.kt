package com.fintrackai.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fintrackai.R
import com.fintrackai.ui.components.PrimaryButton
import com.fintrackai.ui.theme.LocalExtendedColors
import com.fintrackai.ui.theme.Spacing

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ext = LocalExtendedColors.current
    val context = LocalContext.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onAuthSuccess()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = R.drawable.logo,
                contentDescription = "Expensly logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Expensly",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                    fontSize = 48.sp
                ),
                color = ext.text
            )

            Spacer(modifier = Modifier.height(56.dp))

            Text(
                text = "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = ext.textTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            PrimaryButton(
                text = AuthConstants.CONTINUE_WITH_GOOGLE,
                onClick = { viewModel.signInWithGoogle(context) },
                loading = state.loading,
                enabled = !state.loading
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
