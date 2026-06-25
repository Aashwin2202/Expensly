package com.fintrackai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fintrackai.domain.category.CategoryCatalogHelper
import com.fintrackai.domain.model.CategoryStat
import com.fintrackai.domain.model.CustomCategory
import com.fintrackai.ui.theme.LocalExtendedColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PieChartCard(
    stats: List<CategoryStat>,
    customCategories: List<CustomCategory> = emptyList(),
    totalOverride: Double? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    val stats = stats.filter { it.category.lowercase() != "investment" }
    val statsTotal = stats.sumOf { it.amount }
    val total = if (totalOverride != null && totalOverride > statsTotal) totalOverride else statsTotal
    if (statsTotal <= 0 || stats.isEmpty()) return

    val fallbackColors = ext.chartColors
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))

    val resolvedColors: List<Color> = stats.mapIndexed { index, stat ->
        val hex = CategoryCatalogHelper.categoryColor(stat.category, customCategories)
        runCatching {
            val cleaned = hex.trimStart('#')
            val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
            Color(argb.toLong(16).toInt())
        }.getOrElse { fallbackColors[index % fallbackColors.size] }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                var startAngle = -90f
                stats.forEachIndexed { index, stat ->
                    val sweep = (stat.amount / total * 360).toFloat()
                    val color = resolvedColors[index]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 48f, cap = StrokeCap.Butt),
                        topLeft = Offset(24f, 24f),
                        size = Size(size.width - 48f, size.height - 48f)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("₹${formatter.format(total.toLong())}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ext.text)
                Text("Total", fontSize = 12.sp, color = ext.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val leftItems = stats.filterIndexed { i, _ -> i % 2 == 0 }
        val rightItems = stats.filterIndexed { i, _ -> i % 2 == 1 }
        val rows = maxOf(leftItems.size, rightItems.size)
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val leftStat = leftItems.getOrNull(row)
                val rightStat = rightItems.getOrNull(row)
                val leftIndex = row * 2
                val rightIndex = row * 2 + 1

                // Left cell — always half the row width
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leftStat != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(resolvedColors[leftIndex])
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = CategoryCatalogHelper.categoryLabel(leftStat.category, customCategories),
                            fontSize = 12.sp,
                            color = ext.text,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${"%.1f".format(leftStat.amount / total * 100)}%",
                            fontSize = 12.sp,
                            color = ext.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Right cell — always half the row width
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rightStat != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(resolvedColors[rightIndex])
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = CategoryCatalogHelper.categoryLabel(rightStat.category, customCategories),
                            fontSize = 12.sp,
                            color = ext.text,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${"%.1f".format(rightStat.amount / total * 100)}%",
                            fontSize = 12.sp,
                            color = ext.textSecondary
                        )
                    }
                }
            }
        }
    }
}
