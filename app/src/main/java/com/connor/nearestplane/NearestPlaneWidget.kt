package com.connor.nearestplane

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.util.concurrent.TimeUnit

class NearestPlaneWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = AppSettings.appearance(context)
        provideContent {
            GlanceTheme { Body(currentState(), look) }
        }
    }

    @Composable
    private fun Body(prefs: Preferences, look: AppSettings.Appearance) {
        val status = prefs[WidgetState.STATUS] ?: "never"

        // wrapContentHeight rather than fillMaxSize: the tile shrinks to its
        // content instead of padding out to the full cell.
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(WidgetPalette.background(look))
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(actionRunCallback<RefreshAction>())
        ) {
            when (status) {
                "no_permission" -> {
                    Line("Location off", 16, look, FontWeight.Medium)
                    Line("Open the app to grant access", 12, look, muted = true)
                }
                "never" -> {
                    Line("Tap to find a plane", 16, look, FontWeight.Medium)
                }
                "error" -> {
                    Line("Tap to retry", 15, look, FontWeight.Medium)
                    Line(prefs[WidgetState.STALE_NOTE].orEmpty(), 12, look, muted = true)
                }
                else -> Content(prefs, look)
            }
        }
    }

    @Composable
    private fun Content(prefs: Preferences, look: AppSettings.Appearance) {
        val emergency = prefs[WidgetState.EMERGENCY].orEmpty()

        // Line 1: callsign, emergency flag if any, airline pushed alongside.
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Line(prefs[WidgetState.TITLE].orEmpty(), 19, look, FontWeight.Bold)
            if (emergency.isNotBlank()) {
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = emergency,
                    style = TextStyle(
                        fontSize = WidgetPalette.size(11, look).sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFFEF5350))
                    )
                )
            }
            val airline = prefs[WidgetState.AIRLINE].orEmpty()
            if (airline.isNotBlank()) {
                Spacer(GlanceModifier.width(6.dp))
                Line(airline, 12, look, muted = true, maxLines = 1)
            }
        }

        // Line 2: route, with timing appended so it doesn't need its own row.
        val route = prefs[WidgetState.ROUTE].orEmpty()
        val timing = prefs[WidgetState.TIMING].orEmpty()
        if (route.isNotBlank()) {
            Line(
                listOf(route, timing).filter { it.isNotBlank() }.joinToString("  "),
                13, look, FontWeight.Medium, maxLines = 1
            )
        }

        // Line 3: full type name.
        Line(prefs[WidgetState.TYPE_NAME].orEmpty(), 13, look, maxLines = 1)

        // Line 4: distance, altitude, speed and age, all on one row.
        val tail = listOf(
            prefs[WidgetState.POSITION].orEmpty(),
            prefs[WidgetState.DETAIL].orEmpty(),
            prefs[WidgetState.STALE_NOTE].orEmpty().ifBlank {
                ageLabel(prefs[WidgetState.UPDATED_AT] ?: 0L)
            }
        ).filter { it.isNotBlank() }.joinToString(" · ")
        Line(tail, 12, look, muted = true, maxLines = 1)
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

/** Tapping the tile queues an immediate refresh. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        RefreshWorker.refreshNow(context)
    }
}
