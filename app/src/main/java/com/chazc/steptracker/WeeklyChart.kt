package com.chazc.steptracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chazc.steptracker.ui.theme.BandageWhite

@Composable
fun WeeklyChart(data: List<DailySummary>, goal: Int, modifier: Modifier = Modifier) {
    val maxSteps = (data.maxOfOrNull { it.steps } ?: 1000).coerceAtLeast(goal).toFloat()
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeSecondary = MaterialTheme.colorScheme.secondary

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val spacing = 12.dp.toPx()
            val barWidth = (size.width - (spacing * (data.size + 1))) / data.size
            val chartHeight = size.height

            data.forEachIndexed { index, summary ->
                val barHeight = (summary.steps.toFloat() / maxSteps) * chartHeight
                val x = spacing + index * (barWidth + spacing)
                val y = chartHeight - barHeight

                drawRoundRect(
                    color = if (summary.steps >= goal) themePrimary else themeSecondary.copy(alpha = 0.6f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }

            // Baseline
            drawLine(
                color = BandageWhite.copy(alpha = 0.2f),
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 1.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { summary ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = summary.label,
                        color = BandageWhite.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
