package com.chazc.steptracker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

class StepTrackerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            WidgetRenderer.update(context, appWidgetManager, widgetId)
        }

        // Ask the service for fresh numbers. The widget above already shows the last saved values,
        // so if the system refuses to start the service from here nothing is lost.
        val refreshIntent = Intent(context, StepListenerService::class.java).apply {
            action = StepListenerService.ACTION_REFRESH_WIDGET
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(refreshIntent)
            } else {
                context.startService(refreshIntent)
            }
        } catch (e: Exception) {
            Log.w("StepTrackerWidget", "Could not start service for refresh: ${e.message}")
        }
    }

    /** Called when the user resizes the widget: redraw for the new size (hides or shows rows). */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetRenderer.update(context, appWidgetManager, appWidgetId)
    }
}
