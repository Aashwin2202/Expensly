package com.fintrackai.ui.wrapped

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrackai.domain.format.AmountCompactFormatHelper
import com.fintrackai.domain.wrapped.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PAGE_DURATION_MS = 5000L
private const val TOTAL_PAGES = 8

private val cardGradients = listOf(
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460)),
    listOf(Color(0xFF141E30), Color(0xFF243B55)),
    listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF415A77)),
    listOf(Color(0xFF1A1A2E), Color(0xFF16213E)),
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    listOf(Color(0xFF141E30), Color(0xFF243B55)),
    listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF415A77)),
    listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460)),
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
)

@Composable
fun WrappedScreen(
    onClose: () -> Unit,
    viewModel: WrappedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showIntro by remember { mutableStateOf(true) }

    when {
        state.loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        !state.hasData || state.insights == null -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 28.dp, end = 8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😴", fontSize = 56.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No spending data\nfor last month",
                        fontSize = 22.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = onClose) {
                        Text("Close", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
        else -> {
            if (showIntro) {
                WrappedIntroScreen(
                    monthName = state.insights!!.monthDisplayName,
                    onTap = { showIntro = false }
                )
            } else {
                WrappedPager(insights = state.insights!!, onClose = onClose)
            }
        }
    }
}

@Composable
private fun WrappedIntroScreen(
    monthName: String,
    onTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "intro")

    // Pulsing glow behind the content
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale"
    )
    // Sweeping shimmer on subtitle
    val shimmer by infiniteTransition.animateFloat(
        initialValue = -300f, targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "shimmer"
    )

    // Entrance: alpha only — no scale/offset so layout is never disturbed
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val emojiAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 0),
        label = "emojiAlpha"
    )
    val monthAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 250),
        label = "monthAlpha"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 550),
        label = "subtitleAlpha"
    )
    val dividerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, delayMillis = 800),
        label = "dividerAlpha"
    )
    val hintAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, delayMillis = 1100),
        label = "hintAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A14))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        // ── Background glow blobs (purely decorative, behind everything) ──
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.Center)
                .offset(y = (-80).dp)
                .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                .blur(100.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF4F1FB8).copy(alpha = 0.55f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomCenter)
                .offset(x = 60.dp, y = (-100).dp)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF0EA5E9).copy(alpha = 0.4f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // ── All text content in one Column, centred on screen ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp)
                .statusBarsPadding()
        ) {
            Text(
                text = "✨",
                fontSize = 48.sp,
                modifier = Modifier.graphicsLayer { alpha = emojiAlpha }
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = monthName,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 58.sp,
                modifier = Modifier.graphicsLayer { alpha = monthAlpha }
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Expenses Wrapped",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF818CF8),
                            Color(0xFFC084FC),
                            Color(0xFF38BDF8),
                            Color(0xFF818CF8),
                        ),
                        startX = shimmer,
                        endX = shimmer + 600f
                    )
                ),
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha }
            )

            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = dividerAlpha }
                    .width(56.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFF818CF8), Color.Transparent)
                        )
                    )
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Tap anywhere to begin",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 1.5.sp,
                modifier = Modifier.graphicsLayer { alpha = hintAlpha }
            )
        }
    }
}

@Composable
private fun WrappedPager(
    insights: WrappedInsights,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })
    val scope = rememberCoroutineScope()

    var isPaused by remember { mutableStateOf(false) }
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(pagerState.currentPage, isPaused) {
        if (isPaused) return@LaunchedEffect
        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            1f,
            tween(durationMillis = PAGE_DURATION_MS.toInt(), easing = LinearEasing)
        )
        // Use scrollToPage (instant snap) to avoid half-card issue
        if (pagerState.currentPage < TOTAL_PAGES - 1) {
            pagerState.scrollToPage(pagerState.currentPage + 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        val halfWidth = size.width / 2
                        if (offset.x < halfWidth) {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.scrollToPage(pagerState.currentPage - 1) }
                            }
                        } else {
                            if (pagerState.currentPage < TOTAL_PAGES - 1) {
                                scope.launch { pagerState.scrollToPage(pagerState.currentPage + 1) }
                            }
                        }
                    }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) { page ->
            val gradient = Brush.verticalGradient(cardGradients[page % cardGradients.size])
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                when (page) {
                    0 -> TotalSpendCard(insights)
                    1 -> TopCategoryCard(insights)
                    2 -> TopMerchantCard(insights)
                    3 -> MostFrequentMerchantCard(insights)
                    4 -> MostExpensiveDayCard(insights)
                    5 -> LargestTransactionCard(insights)
                    6 -> SpendingTimeCard(insights)
                    7 -> TransactionBehaviorCard(insights)
                }
            }
        }

        StoryProgressBar(
            totalPages = TOTAL_PAGES,
            currentPage = pagerState.currentPage,
            progress = progressAnim.value,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .statusBarsPadding()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 28.dp, end = 8.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun StoryProgressBar(
    totalPages: Int,
    currentPage: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(totalPages) { index ->
            val fillFraction = when {
                index < currentPage -> 1f
                index == currentPage -> progress
                else -> 0f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fillFraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

private val categoryEmojiMap = mapOf(
    "food" to "🍕", "groceries" to "🛒", "shopping" to "🛍️",
    "travel" to "✈️", "rent" to "🏠", "entertainment" to "🎬",
    "bills" to "📄", "health" to "💊", "salary" to "💰",
    "investment" to "📈", "others" to "❔"
)

private fun categoryEmoji(name: String): String =
    categoryEmojiMap[name.lowercase()] ?: "🏷️"

// ── Card Composables ───────────────────────────────────────────────────────

@Composable
private fun TotalSpendCard(insights: WrappedInsights) {
    CardContent {
        Text("💸", fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "${insights.monthDisplayName} Summary",
            fontSize = 20.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "You spent",
            fontSize = 28.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "₹${AmountCompactFormatHelper.formatCompact(insights.totalSpend)}",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TopCategoryCard(insights: WrappedInsights) {
    val cat = insights.topCategory
    CardContent {
        Text("🏆", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Your top category", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(24.dp))
        if (cat != null) {
            Text(categoryEmoji(cat.name), fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                cat.name,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 58.sp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "₹${AmountCompactFormatHelper.formatCompact(cat.amount)}",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${cat.sharePercent.roundToInt()}% of your total spending",
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        } else {
            Text("No category data", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun TopMerchantCard(insights: WrappedInsights) {
    val m = insights.topMerchant
    CardContent {
        Text("💰", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Highest spending", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(24.dp))
        if (m != null) {
            Text(
                m.name,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 52.sp
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "₹${AmountCompactFormatHelper.formatCompact(m.amount)}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${m.transactionCount} transactions",
                fontSize = 22.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        } else {
            Text("No merchant data", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun MostFrequentMerchantCard(insights: WrappedInsights) {
    val m = insights.mostFrequentMerchant
    CardContent {
        Text("🔁", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Most frequent", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(24.dp))
        if (m != null) {
            Text(
                m.name,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 52.sp
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "${m.transactionCount} transactions",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF60A5FA)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "₹${AmountCompactFormatHelper.formatCompact(m.amount)} spent",
                fontSize = 22.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        } else {
            Text("No merchant data", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun TransactionBehaviorCard(insights: WrappedInsights) {
    CardContent {
        Text("⚡", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Your activity", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(24.dp))
        Text(
            "${insights.totalTransactions}",
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "transactions",
            fontSize = 28.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))
        Surface(
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "~₹${AmountCompactFormatHelper.formatCompact(insights.avgPerDay)}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "average per day",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun MostExpensiveDayCard(insights: WrappedInsights) {
    val day = insights.mostExpensiveDay
    CardContent {
        Text("📅", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Biggest spending day", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(24.dp))
        if (day != null) {
            Text(
                day.displayDate,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 58.sp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "₹${AmountCompactFormatHelper.formatCompact(day.amount)}",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "spent in a single day",
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        } else {
            Text("No data", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun LargestTransactionCard(insights: WrappedInsights) {
    val tx = insights.largestTransaction
    CardContent {
        Text("🤯", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Largest single transaction", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(24.dp))
        if (tx != null) {
            Text(
                "₹${AmountCompactFormatHelper.formatCompact(tx.amount)}",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF87171),
                lineHeight = 74.sp
            )
            Spacer(Modifier.height(20.dp))
            Text(
                tx.merchant,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tx.category,
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        } else {
            Text("No data", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SpendingTimeCard(insights: WrappedInsights) {
    val pattern = insights.spendingTimePattern
    CardContent {
        Text("When you spend", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(28.dp))
        Text(
            pattern.emoji,
            fontSize = 56.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You spend most in the",
            fontSize = 24.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            pattern.dominantPeriod,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(28.dp))

        val bucketOrder = listOf("Morning", "Afternoon", "Evening", "Night")
        val bucketEmojis = mapOf("Morning" to "☀️", "Afternoon" to "🌤️", "Evening" to "🌆", "Night" to "🌙")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (name in bucketOrder) {
                val bucket = pattern.buckets[name] ?: continue
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Text(
                        "${bucketEmojis[name]} $name",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.width(120.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((bucket.percent / 100f).coerceIn(0.0, 1.0).toFloat())
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(alpha = 0.8f))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${bucket.percent.roundToInt()}%",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun CardContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}
