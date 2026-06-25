package com.fintrackai.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.ui.components.PrimaryButton
import com.fintrackai.ui.theme.LocalExtendedColors
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGES = 3

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES })
    val scope = rememberCoroutineScope()
    val ext = LocalExtendedColors.current

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A14))
    ) {
        // Purple glow blob
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopCenter)
                .offset(y = 60.dp)
                .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                .blur(110.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF4F1FB8).copy(alpha = 0.45f), Color.Transparent)),
                    CircleShape
                )
        )
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> OnboardingPage1(ext.chartColors)
                    1 -> OnboardingPage2()
                    2 -> OnboardingPage3()
                }
            }

            // Dots + button area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 48.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(ONBOARDING_PAGES) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == pagerState.currentPage) 20.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) Color(0xFF818CF8)
                                    else Color.White.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                if (pagerState.currentPage < ONBOARDING_PAGES - 1) {
                    PrimaryButton(
                        text = "Next",
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
                    )
                } else {
                    PrimaryButton(
                        text = "Get Started",
                        onClick = onFinish
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage1(chartColors: List<Color>) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val emojiAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "e1")
    val titleAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500, delayMillis = 200), label = "t1")
    val subAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500, delayMillis = 400), label = "s1")
    val chartAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(600, delayMillis = 600), label = "c1")

    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(1200, delayMillis = 600, easing = EaseOutCubic),
        label = "donut"
    )

    val segments = listOf(
        Pair(0.32f, chartColors.getOrElse(0) { Color(0xFF4A7FA5) }),
        Pair(0.20f, chartColors.getOrElse(1) { Color(0xFF5A9E8A) }),
        Pair(0.18f, chartColors.getOrElse(2) { Color(0xFFB07D62) }),
        Pair(0.16f, chartColors.getOrElse(3) { Color(0xFF9B7EB8) }),
        Pair(0.14f, chartColors.getOrElse(4) { Color(0xFF6A9EB5) }),
    )
    val segmentLabels = listOf("Food", "Transport", "Shopping", "Bills", "Other")

    PageContent {
        Text("📊", fontSize = 52.sp, modifier = Modifier.graphicsLayer { alpha = emojiAlpha })
        Spacer(Modifier.height(20.dp))
        Text(
            "See where your\nmoney goes",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp,
            modifier = Modifier.graphicsLayer { alpha = titleAlpha }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Visualise spending through charts",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.graphicsLayer { alpha = subAlpha }
        )
        Spacer(Modifier.height(36.dp))

        // Donut chart card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = chartAlpha }
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(130.dp)) {
                        var startAngle = -90f
                        segments.forEach { (pct, color) ->
                            val sweep = pct * 360f * animProgress
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = 36f, cap = StrokeCap.Butt),
                                topLeft = Offset(18f, 18f),
                                size = Size(size.width - 36f, size.height - 36f)
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("₹38K", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                        Text("/ month", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    segments.forEachIndexed { i, (_, color) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                segmentLabels.getOrElse(i) { "" },
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage2() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val emojiAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "e2")
    val titleAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500, delayMillis = 200), label = "t2")
    val subAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500, delayMillis = 400), label = "s2")
    val cardAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(600, delayMillis = 600), label = "ca2")

    PageContent {
        Text("📩", fontSize = 52.sp, modifier = Modifier.graphicsLayer { alpha = emojiAlpha })
        Spacer(Modifier.height(20.dp))
        Text(
            "Zero typing\nrequired",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp,
            modifier = Modifier.graphicsLayer { alpha = titleAlpha }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Expensly reads your bank SMS and\nlogs transactions instantly.",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.graphicsLayer { alpha = subAlpha }
        )
        Spacer(Modifier.height(36.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = cardAlpha },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mock SMS bubble
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E2A3A))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SBI", fontSize = 11.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your a/c XX1234 debited INR 850.00 on 14-Jun-26 at SWIGGY. Avl Bal: INR 24,310.00",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        lineHeight = 20.sp
                    )
                }
            }

            // Arrow
            Text("↓", fontSize = 20.sp, color = Color(0xFF818CF8), modifier = Modifier.align(Alignment.CenterHorizontally))

            // Parsed transaction card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4F1FB8).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🍕", fontSize = 18.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Swiggy", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Food", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    Text("−₹850", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage3() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val emojiAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "e3")
    val titleAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500, delayMillis = 200), label = "t3")
    val subAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(500, delayMillis = 400), label = "s3")
    val chartAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(600, delayMillis = 600), label = "ch3")
    val barProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 700, easing = EaseOutCubic),
        label = "bar"
    )

    val budgetBars = listOf(
        Triple("Food", 0.55f, Color(0xFF4A7FA5)),
        Triple("Shopping", 0.85f, Color(0xFFF87171)),
        Triple("Transport", 0.40f, Color(0xFF5A9E8A)),
        Triple("Bills", 0.65f, Color(0xFF9B7EB8)),
    )

    PageContent {
        Text("🎯", fontSize = 52.sp, modifier = Modifier.graphicsLayer { alpha = emojiAlpha })
        Spacer(Modifier.height(20.dp))
        Text(
            "Stay ahead of\nyour budget",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp,
            modifier = Modifier.graphicsLayer { alpha = titleAlpha }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Set limits and get alerts",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.graphicsLayer { alpha = subAlpha }
        )
        Spacer(Modifier.height(36.dp))

        // Budget bar chart card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = chartAlpha }
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                budgetBars.forEach { (label, fill, color) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.width(72.dp)
                        )
                        val barColor = color
                        val barFill = fill
                        val showLimit = fill > 0.75f
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                        ) {
                            val trackH = size.height
                            val trackW = size.width
                            val radius = trackH / 2
                            // Track background
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.12f),
                                size = Size(trackW, trackH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                            )
                            // Filled portion
                            val filledW = (trackW * (barFill * barProgress).coerceIn(0f, 1f))
                            if (filledW > 0f) {
                                drawRoundRect(
                                    color = barColor,
                                    size = Size(filledW, trackH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                                )
                            }
                            // Budget limit line at 80%
                            if (showLimit) {
                                val limitX = trackW * 0.8f
                                drawLine(
                                    color = Color(0xFFFBBF24),
                                    start = Offset(limitX, 0f),
                                    end = Offset(limitX, trackH),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                        Text(
                            "${(fill * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = if (fill >= 0.8f) Color(0xFFF87171) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}
