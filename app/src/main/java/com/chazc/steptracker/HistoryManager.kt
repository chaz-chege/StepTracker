package com.chazc.steptracker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/** Whole-day totals, independent of whether a training session was started. */
data class DailyTotals(val steps: Int, val distanceMeters: Float, val calories: Int)

data class DailySummary(
    val label: String,
    val dateMs: Long,
    val steps: Int,
    val distanceMeters: Float,
    val calories: Int,
    val durationMs: Long,
    val sessions: List<SessionRecord> = emptyList()
) {
    val avgPaceMinPerKm: Float
        get() {
            // Pace is a session measure: session time over session distance, not whole-day distance.
            val distanceKm = sessions.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f
            val durationMin = durationMs / 60000f
            return if (distanceKm > 0.05f) durationMin / distanceKm else 0f
        }
}

data class WeeklyStats(
    val avgSteps: Int,
    val avgDistanceMeters: Float,
    val avgPaceMinPerKm: Float
)

object HistoryManager {
    private const val PREFS_NAME = "step_tracker_history"
    private const val KEY_SESSIONS = "session_history"
    private const val KEY_DAY_PREFIX = "day_"
    private const val MAX_SESSIONS = 1000
    private const val KEEP_DAYS = 120

    @Volatile private var lastPrunedForDate = ""

    fun saveSession(context: Context, session: SessionRecord) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = prefs.getString(KEY_SESSIONS, "") ?: ""

        // Simple CSV-like format: start,end,steps,distance,calories,pace
        val sessionStr = "${session.startTimeMs},${session.endTimeMs},${session.steps},${session.distanceMeters},${session.calories},${session.avgPaceMinPerKm}"
        var newHistory = if (history.isEmpty()) sessionStr else "$history;$sessionStr"

        // Keep the newest sessions only so this string cannot grow without bound.
        val parts = newHistory.split(";")
        if (parts.size > MAX_SESSIONS) newHistory = parts.takeLast(MAX_SESSIONS).joinToString(";")

        prefs.edit().putString(KEY_SESSIONS, newHistory).apply()
    }

    /**
     * Stores the whole-day totals for [date] (yyyy-MM-dd). The service calls this as it persists and
     * once more at midnight, which is what makes the weekly chart show real daily steps instead of
     * only the steps that happened inside a started session.
     */
    fun saveDailyTotals(context: Context, date: String, steps: Int, distanceMeters: Float, calories: Int) {
        if (date.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(KEY_DAY_PREFIX + date, "$steps,$distanceMeters,$calories")

        if (date != lastPrunedForDate) {
            lastPrunedForDate = date
            val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -KEEP_DAYS) }
                .let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it.time) }
            prefs.all.keys
                .filter { it.startsWith(KEY_DAY_PREFIX) && it.removePrefix(KEY_DAY_PREFIX) < cutoff }
                .forEach { editor.remove(it) }
        }
        editor.apply()
    }

    fun getDailyTotals(context: Context, date: String): DailyTotals? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DAY_PREFIX + date, null) ?: return null
        return try {
            val p = raw.split(",")
            DailyTotals(p[0].toInt(), p[1].toFloat(), p[2].toInt())
        } catch (e: Exception) {
            null
        }
    }

    fun getHistory(context: Context): List<SessionRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = prefs.getString(KEY_SESSIONS, "") ?: ""
        if (history.isEmpty()) return emptyList()

        return history.split(";").mapNotNull {
            try {
                val parts = it.split(",")
                SessionRecord(
                    startTimeMs = parts[0].toLong(),
                    endTimeMs = parts[1].toLong(),
                    steps = parts[2].toInt(),
                    distanceMeters = parts[3].toFloat(),
                    calories = parts[4].toInt(),
                    avgPaceMinPerKm = if (parts.size > 5) parts[5].toFloat() else 0f
                )
            } catch (e: Exception) {
                null
            }
        }.reversed() // Most recent first
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SESSIONS).apply()
    }

    fun getHistoryGroupedByDay(context: Context): List<DailySummary> {
        val allHistory = getHistory(context)
        if (allHistory.isEmpty()) return emptyList()

        val grouped = allHistory.groupBy { session ->
            val cal = Calendar.getInstance().apply { timeInMillis = session.startTimeMs }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        return grouped.map { (dateMs, sessions) ->
            val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
            val label = getDayLabel(cal)
            
            DailySummary(
                label = label,
                dateMs = dateMs,
                steps = sessions.sumOf { it.steps },
                distanceMeters = sessions.sumOf { it.distanceMeters.toDouble() }.toFloat(),
                calories = sessions.sumOf { it.calories },
                durationMs = sessions.sumOf { it.endTimeMs - it.startTimeMs },
                sessions = sessions
            )
        }.sortedByDescending { it.dateMs }
    }

    fun getWeeklySummary(context: Context): List<DailySummary> {
        val allHistory = getHistory(context)
        val summaries = mutableListOf<DailySummary>()

        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply { 
                time = Date()
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val year = targetCal.get(Calendar.YEAR)
            val dayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)
            val label = getDayLabel(targetCal)
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(targetCal.time)
            val daily = getDailyTotals(context, dateKey)

            val daySessions = allHistory.filter { session ->
                val sCal = Calendar.getInstance().apply { timeInMillis = session.startTimeMs }
                sCal.get(Calendar.YEAR) == year && sCal.get(Calendar.DAY_OF_YEAR) == dayOfYear
            }

            summaries.add(
                DailySummary(
                    label = label,
                    dateMs = targetCal.timeInMillis,
                    // Whole-day totals when we have them, otherwise fall back to session sums (old data).
                    steps = daily?.steps ?: daySessions.sumOf { it.steps },
                    distanceMeters = daily?.distanceMeters ?: daySessions.sumOf { it.distanceMeters.toDouble() }.toFloat(),
                    calories = daily?.calories ?: daySessions.sumOf { it.calories },
                    durationMs = daySessions.sumOf { it.endTimeMs - it.startTimeMs },
                    sessions = daySessions
                )
            )
        }
        return summaries
    }

    fun getWeeklyAverages(context: Context): WeeklyStats {
        val summary = getWeeklySummary(context)
        if (summary.isEmpty()) return WeeklyStats(0, 0f, 0f)
        
        val totalSteps = summary.sumOf { it.steps }
        val totalDistance = summary.sumOf { it.distanceMeters.toDouble() }.toFloat()
        val totalDuration = summary.sumOf { it.durationMs }
        val sessionDistance = summary.sumOf { day -> day.sessions.sumOf { it.distanceMeters.toDouble() } }.toFloat()
        
        val avgSteps = totalSteps / 7
        val avgDistance = totalDistance / 7
        
        // Pace compares session time with session distance only.
        val distanceKm = sessionDistance / 1000f
        val durationMin = totalDuration / 60000f
        val avgPace = if (distanceKm > 0.05f) durationMin / distanceKm else 0f
        
        return WeeklyStats(avgSteps, avgDistance, avgPace)
    }

    private fun getDayLabel(cal: Calendar): String {
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "T"
            Calendar.FRIDAY -> "F"
            Calendar.SATURDAY -> "S"
            else -> "S"
        }
    }
}
