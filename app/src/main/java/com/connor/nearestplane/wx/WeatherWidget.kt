package com.connor.nearestplane.wx

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.connor.nearestplane.AppSettings
import com.connor.nearestplane.WidgetPalette
import java.util.concurrent.TimeUnit

class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = AppSettings.appearance(context)
        provideContent {
            GlanceTheme { Body(currentState(), look) }
        }
    }

    @Composable
    private fun Body(prefs: Preferences, look: AppSettings.Appearance) {
        val status = prefs[WxState.STATUS] ?: "never"

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(WidgetPalette.background(look))
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(actionStartActivity<WeatherDetailActivity>())
        ) {
            when (status) {
                "no_permission" -> {
                    Line("Location off", 15, look, FontWeight.Medium)
                    Line("Open the app to grant access", 12, look, muted = true)
                }
                "never" -> {
                    Line("Tap for airport weather", 15, look, FontWeight.Medium)
                }
                "error" -> {
                    Line("Tap to retry", 15, look, FontWeight.Medium)
                    Line(prefs[WxState.STALE_NOTE].orEmpty(), 12, look, muted = true)
                }
                else -> {
                    // Row 1: station, category, and age together — the age used
                    // to sit on its own line and earned none of that space.
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Line(prefs[WxState.STATION].orEmpty(), 20, look, FontWeight.Bold)
                        Spacer(GlanceModifier.width(6.dp))
                        val cat = prefs[WxState.CATEGORY].orEmpty()
                        if (cat.isNotBlank()) {
                            Text(
                                text = cat,
                                style = TextStyle(
                                    fontSize = WidgetPalette.size(13, look).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(WidgetPalette.categoryColor(cat, look))
                                )
                            )
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        val note = prefs[WxState.STALE_NOTE].orEmpty()
                            .ifBlank { ageLabel(prefs[WxState.UPDATED_AT] ?: 0L) }
                        Line(note, 11, look, muted = true, maxLines = 1)
                    }

                    // Row 2: decoded summary.
                    Line(prefs[WxState.SUMMARY].orEmpty(), 14, look, maxLines = 1)

                    // Row 3: raw METAR, capped at two lines instead of three.
                    Text(
                        text = prefs[WxState.RAW_METAR].orEmpty(),
                        maxLines = 2,
                        style = TextStyle(
                            fontSize = WidgetPalette.size(11, look).sp,
                            fontFamily = FontFamily.Monospace,
                            color = WidgetPalette.secondaryText(look)
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun Line(
        text: String,
        size: Int,
        look: AppSettings.Appearance,
        weight: FontWeight = FontWeight.Normal,
        muted: Boolean = false,
        maxLines: Int = 2
    ) {
        if (text.isBlank()) return
        Text(
            text = text,
            maxLines = maxLines,
            style = TextStyle(
                fontSize = WidgetPalette.size(size, look).sp,
                fontWeight = weight,
                color = if (muted) WidgetPalette.secondaryText(look)
                else WidgetPalette.primaryText(look)
            )
        )
    }

    private fun ageLabel(updatedAt: Long): String {
        if (updatedAt == 0L) return ""
        val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - updatedAt)
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m ago"
            else -> "${minutes / 60}h ago"
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WxRefreshWorker.schedulePeriodic(context)
        WxRefreshWorker.refreshNow(context)
    }

    /**
     * Second, independent refresh path, driven by updatePeriodMillis in the
     * widget XML through the system's AlarmManager. That survives some
     * conditions that defer WorkManager jobs indefinitely.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WxRefreshWorker.schedulePeriodic(context)
        WxRefreshWorker.refreshNow(context)
    }
}
