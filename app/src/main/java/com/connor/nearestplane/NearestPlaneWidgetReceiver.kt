package com.connor.nearestplane

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class NearestPlaneWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = NearestPlaneWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }

    /**
     * Second, independent refresh path.
     *
     * updatePeriodMillis in the widget XML drives this through the system's
     * AlarmManager, which survives some conditions that defer WorkManager jobs.
     * Belt and braces: whichever fires first wins, and the unique-work policy
     * means a double fire costs one request, not two.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }
}
