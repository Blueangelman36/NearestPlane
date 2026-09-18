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
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.Action
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

        // A tile that says "open the app" and then doesn't when tapped is worse
        // than one that says nothing. Everywhere else, tap opens the detail
        // screen, which also kicks a refresh of the tile behind it — so this
        // still forces a refresh, it just shows you something while it works.
        val tap = if (status == "no_permission") actionStartActivity<MainActivity>()
        else actionStartActivity<PlaneDetailActivity>()

        // wrapContentHeight rather than fillMaxSize: the tile shrinks to its
        // content instead of padding out to the full cell.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(WidgetPalette.background(look))
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(tap),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // defaultWeight so the text takes the room the button doesn't, and
            // the button stays pinned to the right edge at any tile width.
            Column(modifier = GlanceModifier.defaultWeight()) {
                when (status) {
                    "no_permission" -> {
                        Line("No location set", 18, look, FontWeight.Medium)
                        Line("Tap to set it up", 14, look, muted = true)
                    }
                    "never" -> {
                        Line("Tap to find a plane", 18, look, FontWeight.Medium)
                    }
                    "error" -> {
                        Line("Tap to retry", 17, look, FontWeight.Medium)
                        Line(prefs[WidgetState.STALE_NOTE].orEmpty(), 14, look, muted = true)
                    }
                    else -> Content(prefs, look)
                }
            }

            // No refresh button when there's no location to refresh from: the
            // button would be honest about running and useless about the result.
            if (status != "no_permission") {
                RefreshButton(look, actionRunCallback<PlaneRefreshAction>())
            }
        }
    }

    /**
     * A tap target of its own, inside a tile that is itself tappable. The inner
     * clickable wins, so this refreshes in place instead of opening the detail
     * screen — which is the whole point: the tile is usually right, and you
     * just want it to be right *now*.
     */
    @Composable
    private fun RefreshButton(look: AppSettings.Appearance, onClick: Action) {
        Box(
            modifier = GlanceModifier
                // Padding rather than a bigger glyph: it buys a tap target
                // without spending tile width on something read at a glance.
                .padding(start = 8.dp, top = 2.dp, bottom = 8.dp, end = 2.dp)
                .clickable(onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier.size(WidgetPalette.size(18, look).dp),
                colorFilter = ColorFilter.tint(WidgetPalette.secondaryText(look))
            )
        }
    }

    @Composable
    private fun Content(prefs: Preferences, look: AppSettings.Appearance) {
        val emergency = prefs[WidgetState.EMERGENCY].orEmpty()

        // Line 1: callsign, emergency flag if any, airline pushed alongside.
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Line(prefs[WidgetState.TITLE].orEmpty(), 22, look, FontWeight.Bold)
            if (emergency.isNotBlank()) {
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = emergency,
                    style = TextStyle(
                        fontSize = WidgetPalette.size(13, look).sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFFEF5350))
                    )
                )
            }
            val airline = prefs[WidgetState.AIRLINE].orEmpty()
            if (airline.isNotBlank()) {
                Spacer(GlanceModifier.width(6.dp))
                Line(airline, 14, look, muted = true, maxLines = 1)
            }
        }

        // Line 2: route, with timing appended so it doesn't need its own row.
        val route = prefs[WidgetState.ROUTE].orEmpty()
        val timing = prefs[WidgetState.TIMING].orEmpty()
        if (route.isNotBlank()) {
            Line(
                listOf(route, timing).filter { it.isNotBlank() }.joinToString("  "),
                15, look, FontWeight.Medium, maxLines = 1
            )
        }

        // Line 3: full type name.
        Line(prefs[WidgetState.TYPE_NAME].orEmpty(), 15, look, FontWeight.Medium, maxLines = 1)

        // Line 4: distance, altitude, speed and age, all on one row.
        val tail = listOf(
            prefs[WidgetState.POSITION].orEmpty(),
            prefs[WidgetState.DETAIL].orEmpty(),
            prefs[WidgetState.STALE_NOTE].orEmpty().ifBlank {
                ageLabel(prefs[WidgetState.UPDATED_AT] ?: 0L)
            }
        ).filter { it.isNotBlank() }.joinToString(" · ")
        Line(tail, 14, look, muted = true, maxLines = 1)
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
                fontWeight = WidgetPalette.weight(weight, look),
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

/** The refresh button: queue a fetch, and say so while it runs. */
class PlaneRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // The fetch takes seconds and nothing on the tile moves until it lands,
        // so without this the button reads as broken and gets tapped again.
        // The worker overwrites this note on both its success and failure paths,
        // so it clears itself with no special handling here.
        runCatching {
            updateAppWidgetState(context, glanceId) {
                it[WidgetState.STALE_NOTE] = "refreshing…"
            }
            NearestPlaneWidget().update(context, glanceId)
        }
        RefreshWorker.refreshNow(context)
    }
}
