package com.chazc.steptracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chazc.steptracker.ui.theme.BandageWhite
import com.chazc.steptracker.ui.theme.OnigiriBlack
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dailySummaries = remember { HistoryManager.getHistoryGroupedByDay(context) }
    val weeklySummary = remember { HistoryManager.getWeeklySummary(context) }
    val weeklyAverages = remember { HistoryManager.getWeeklyAverages(context) }
    val dailyGoal = remember { AppSettings.getDailyGoal(context) }

    Surface(modifier = Modifier.fillMaxSize(), color = OnigiriBlack) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TRAINING HISTORY",
                    color = BandageWhite,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )
                TextButton(onClick = onBack) {
                    Text(text = "BACK", color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            WeeklyAveragesBanner(weeklyAverages)

            Spacer(modifier = Modifier.height(16.dp))

            WeeklyChart(data = weeklySummary, goal = dailyGoal)

            Spacer(modifier = Modifier.height(24.dp))

            if (dailySummaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "NO LOGS YET. START TRAINING.",
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(dailySummaries) { summary ->
                        DailyHistoryBlock(summary)
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyAveragesBanner(stats: WeeklyStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF161A17),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(text = "AVG STEPS", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                Text(text = "${stats.avgSteps}", color = BandageWhite, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Box(modifier = Modifier.width(1.dp).height(30.dp).align(Alignment.CenterVertically).background(BandageWhite.copy(alpha = 0.1f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(text = "AVG DISTANCE", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                Text(text = "%.2f km".format(stats.avgDistanceMeters / 1000f), color = BandageWhite, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Box(modifier = Modifier.width(1.dp).height(30.dp).align(Alignment.CenterVertically).background(BandageWhite.copy(alpha = 0.1f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(text = "AVG PACE", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                Text(text = formatPaceDisplay(stats.avgPaceMinPerKm).replace("/km", ""), color = BandageWhite, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun DailyHistoryBlock(summary: DailySummary) {
    val dateFormatter = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val dateStr = dateFormatter.format(Date(summary.dateMs))
    val isToday = Calendar.getInstance().apply { 
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis == summary.dateMs

    Column {
        Text(
            text = if (isToday) "TODAY'S TRAINING" else dateStr.uppercase(),
            color = if (isToday) MaterialTheme.colorScheme.secondary else BandageWhite.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF161A17), // CardSurface
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Daily Summary Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${summary.steps}", 
                            color = BandageWhite, 
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "TOTAL STEPS", 
                                color = MaterialTheme.colorScheme.secondary, 
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• %.2f km".format(summary.distanceMeters / 1000f),
                                color = BandageWhite.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatPaceDisplay(summary.avgPaceMinPerKm),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "AVG PACE", color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BandageWhite.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                // Sessions List
                summary.sessions.forEach { record ->
                    SessionItem(record)
                    if (record != summary.sessions.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItem(record: SessionRecord) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val startTime = timeFormatter.format(Date(record.startTimeMs))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = startTime, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = "${record.steps} steps", color = BandageWhite, fontSize = 14.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VerticalStat(label = "DISTANCE", value = "%.2f km".format(record.distanceMeters / 1000f))
            VerticalStat(label = "PACE", value = formatPaceDisplay(record.avgPaceMinPerKm))
        }
    }
}

@Composable
fun VerticalStat(label: String, value: String) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Column(horizontalAlignment = Alignment.End) {
        Text(text = value, color = BandageWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = secondaryColor.copy(alpha = 0.7f), fontSize = 8.sp, letterSpacing = 0.5.sp)
    }
}
