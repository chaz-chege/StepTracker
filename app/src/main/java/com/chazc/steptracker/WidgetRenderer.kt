package com.chazc.steptracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the complete home-screen widget in one place, so the widget provider and the tracking
 * service can never disagree about how it looks or what the buttons do.
 *
 * Why this exists. The old service rebuilt the widget on every step with setBackgroundColor and
 * left out the button click handlers. On MIUI that turned the rounded card into a hard rectangle,
 * flattened the buttons and could drop the START and STOP clicks after the first refresh.
 * Here every update is a full RemoteViews that includes the click handlers.
 *
 * Everything coloured at run time is a tiny bitmap set with setImageViewBitmap. That is the one
 * RemoteViews call guaranteed to work on every launcher, unlike reflection-based calls such as
 * setColorFilter that some launchers refuse.
 */
object WidgetRenderer {
    private const val TAG = "WidgetRenderer"
    private const val PREFS_NAME = "step_tracker_prefs"

    private const val DEFAULT_WIDTH_DP = 250
    private const val DEFAULT_HEIGHT_DP = 180
    private const val MAX_BACKGROUND_PX = 560      // longest side of the background bitmap, about 1 MB
    private const val CARD_RADIUS_DP = 15f         // card is 16 dp, the picture sits 1 dp inside it
    private const val BACKGROUND_ALPHA = 115       // 45 percent, same as the old XML
    private const val BUTTON_PX_PER_DP = 2f
    private const val BUTTON_HEIGHT_DP = 32f
    private const val BUTTON_RADIUS_DP = 8f
    private const val DIM_ALPHA = 115

    // Height tiers (dp). Below each threshold that part is hidden so nothing is ever clipped.
    private const val SHOW_STATS_FROM_DP = 150
    private const val SHOW_TITLE_FROM_DP = 165
    private const val SHOW_QUOTE_FROM_DP = 200

    private var cachedBackgroundKey: String? = null
    private var cachedBackground: Bitmap? = null
    private val buttonCache = HashMap<String, Bitmap>()

    private class Snapshot(
        val steps: Int,
        val distanceKm: Float,
        val calories: Int,
        val activeMinutes: Int,
        val goal: Int,
        val sessionActive: Boolean
    )

    /** Refresh every widget the user has placed. Safe to call from any thread. */
    fun update(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, StepTrackerWidget::class.java))
        for (id in ids) update(app, manager, id)
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        try {
            manager.updateAppWidget(widgetId, build(context.applicationContext, manager.getAppWidgetOptions(widgetId)))
        } catch (e: Exception) {
            // A launcher or memory hiccup must never be able to stop step counting.
            Log.w(TAG, "Widget update failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------------------------------

    private fun build(context: Context, options: Bundle): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_step_tracker)
        val (widthDp, heightDp) = sizeDp(context, options)
        val data = snapshot(context)
        val theme = StepTrackerState.selectedCharacter.value

        views.setViewVisibility(R.id.widget_stats_row, visibleIf(heightDp >= SHOW_STATS_FROM_DP))
        views.setViewVisibility(R.id.widget_moniker, visibleIf(heightDp >= SHOW_TITLE_FROM_DP))
        views.setViewVisibility(R.id.widget_quote, visibleIf(heightDp >= SHOW_QUOTE_FROM_DP))

        views.setTextViewText(R.id.widget_moniker, theme.moniker)
        views.setTextColor(R.id.widget_moniker, theme.secondary.toArgb())
        views.setTextViewText(R.id.widget_quote, "\"${QuotesProvider.getQuoteOfDay(theme)}\"")

        views.setTextViewText(R.id.widget_steps, "%,d %s".format(Locale.US, data.steps, theme.stats.stepsLabel))
        val goal = max(data.goal, 1)
        views.setProgressBar(R.id.widget_progress, goal, data.steps.coerceIn(0, goal), false)

        views.setTextViewText(R.id.widget_distance, "%.2f km".format(Locale.US, data.distanceKm))
        views.setTextViewText(R.id.widget_distance_label, theme.stats.distanceLabel)
        views.setTextColor(R.id.widget_distance_label, theme.secondary.toArgb())

        views.setTextViewText(R.id.widget_calories, "${data.calories} kcal")
        views.setTextViewText(R.id.widget_calories_label, theme.stats.caloriesLabel)
        views.setTextColor(R.id.widget_calories_label, theme.secondary.toArgb())

        views.setTextViewText(R.id.widget_active_min, "${data.activeMinutes} min")
        views.setTextViewText(R.id.widget_active_label, theme.stats.activeLabel)
        views.setTextColor(R.id.widget_active_label, theme.secondary.toArgb())

        // Background picture, pre-rounded to this widget's exact shape.
        val backgroundRes = theme.backgroundRes
        val background = if (backgroundRes != null) backgroundBitmap(context, backgroundRes, widthDp, heightDp) else null
        if (background != null) {
            views.setViewVisibility(R.id.widget_bg_image, View.VISIBLE)
            views.setImageViewBitmap(R.id.widget_bg_image, background)
        } else {
            views.setViewVisibility(R.id.widget_bg_image, View.GONE)
        }

        // Buttons: the one that applies right now is solid, the other is dimmed.
        val buttonWidthDp = max(40f, (widthDp - 28) / 2f)
        val startColor = theme.primary.toArgb()
        val stopColor = theme.tertiary.toArgb()
        views.setImageViewBitmap(
            R.id.widget_btn_start_bg,
            buttonBitmap(if (data.sessionActive) withAlpha(startColor, DIM_ALPHA) else startColor, buttonWidthDp)
        )
        views.setImageViewBitmap(
            R.id.widget_btn_stop_bg,
            buttonBitmap(if (data.sessionActive) stopColor else withAlpha(stopColor, DIM_ALPHA), buttonWidthDp)
        )
        views.setTextViewText(R.id.widget_btn_start_label, if (data.sessionActive) "TRAINING" else "START")

        // Click handlers are part of every update, so they can never be lost.
        views.setOnClickPendingIntent(R.id.widget_btn_start, servicePendingIntent(context, 0, StepListenerService.ACTION_START_SESSION))
        views.setOnClickPendingIntent(R.id.widget_btn_stop, servicePendingIntent(context, 1, StepListenerService.ACTION_STOP_SESSION))
        launchPendingIntent(context)?.let { views.setOnClickPendingIntent(R.id.widget_root, it) }

        return views
    }

    private fun visibleIf(condition: Boolean) = if (condition) View.VISIBLE else View.GONE

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    // ------------------------------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------------------------------

    private fun snapshot(context: Context): Snapshot {
        var steps = StepTrackerState.steps.value
        var distanceKm = StepTrackerState.dailyDistanceKm.value
        var calories = StepTrackerState.calories.value
        var active = StepTrackerState.activeMinutes.value

        if (steps == 0) {
            // The process may be fresh and the service not loaded yet. Use what it last saved, but only
            // if that is from today, so the widget never shows yesterday's number.
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
            val saved = prefs.getInt("current_steps", 0)
            if (saved > 0 && prefs.getString("last_date", null) == today) {
                steps = saved
                distanceKm = prefs.getFloat("daily_distance", 0f) / 1000f
                calories = (saved * 0.04f).toInt()
                active = saved / 100
            }
        }
        return Snapshot(
            steps = steps,
            distanceKm = distanceKm,
            calories = calories,
            activeMinutes = active,
            goal = AppSettings.getDailyGoal(context),
            sessionActive = StepTrackerState.isSessionActive.value
        )
    }

    /** Current widget size in dp as reported by the launcher; falls back to a medium size. */
    private fun sizeDp(context: Context, options: Bundle): Pair<Int, Int> {
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(
            if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0
        )
        val height = options.getInt(
            if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0
        )
        return Pair(if (width > 0) width else DEFAULT_WIDTH_DP, if (height > 0) height else DEFAULT_HEIGHT_DP)
    }

    // ------------------------------------------------------------------------------------------
    // Bitmaps
    // ------------------------------------------------------------------------------------------

    /**
     * The character picture, centre-cropped to the widget's aspect ratio with anti-aliased rounded
     * corners. Decoded at no more than the size it is shown (the source is 1367 x 768 and, without
     * these options, Android would scale it up to roughly 2700 x 1500 on a phone like the Redmi 10A).
     * The result is cached, so a refresh every few seconds costs nothing.
     */
    private fun backgroundBitmap(context: Context, resId: Int, widthDp: Int, heightDp: Int): Bitmap? {
        val key = "$resId:$widthDp:$heightDp"
        if (key == cachedBackgroundKey) cachedBackground?.let { if (!it.isRecycled) return it }

        return try {
            val pxPerDp = min(2.5f, MAX_BACKGROUND_PX / max(widthDp, heightDp).toFloat())
            val destW = max(1, (widthDp * pxPerDp).toInt())
            val destH = max(1, (heightDp * pxPerDp).toInt())

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeResource(context.resources, resId, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= destW && bounds.outHeight / (sample * 2) >= destH) sample *= 2

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inScaled = false                          // never density-scale the source up
                inPreferredConfig = Bitmap.Config.RGB_565 // photo with no transparency, half the memory
            }
            val source = BitmapFactory.decodeResource(context.resources, resId, options) ?: return null

            val result = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
            val scale = max(destW / source.width.toFloat(), destH / source.height.toFloat())
            val shader = BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate((destW - source.width * scale) / 2f, (destH - source.height * scale) / 2f)
            })
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = shader
                alpha = BACKGROUND_ALPHA
            }
            val radius = CARD_RADIUS_DP * pxPerDp
            Canvas(result).drawRoundRect(RectF(0f, 0f, destW.toFloat(), destH.toFloat()), radius, radius, paint)
            source.recycle()

            cachedBackgroundKey = key
            cachedBackground = result
            result
        } catch (e: Throwable) {
            // OutOfMemoryError included: on a low-RAM phone the widget just goes without a picture.
            Log.w(TAG, "Background not drawn: ${e.message}")
            null
        }
    }

    /** A solid rounded button face in the given colour, sized to match the button on screen. */
    private fun buttonBitmap(color: Int, widthDp: Float): Bitmap {
        val key = "$color:${widthDp.toInt() / 8}"
        buttonCache[key]?.let { if (!it.isRecycled) return it }
        if (buttonCache.size > 12) buttonCache.clear()

        val w = max(8, (widthDp * BUTTON_PX_PER_DP).toInt())
        val h = (BUTTON_HEIGHT_DP * BUTTON_PX_PER_DP).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val radius = BUTTON_RADIUS_DP * BUTTON_PX_PER_DP
        Canvas(bitmap).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
        buttonCache[key] = bitmap
        return bitmap
    }

    // ------------------------------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------------------------------

    private fun servicePendingIntent(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, StepListenerService::class.java).apply { this.action = action }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, flags)
        } else {
            PendingIntent.getService(context, requestCode, intent, flags)
        }
    }

    private fun launchPendingIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 2, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
