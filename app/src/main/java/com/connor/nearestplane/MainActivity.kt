package com.connor.nearestplane

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.connor.nearestplane.wx.WeatherWidget
import com.connor.nearestplane.wx.WxRefreshWorker
import kotlinx.coroutines.launch

/**
 * The app is a setup screen — the widgets are the product.
 */
class MainActivity : ComponentActivity() {

    // Held on the activity, not remembered in the composition: both are changed
    // from outside the app, in system Settings, and the screen has to pick that
    // up when the user comes back. Remembered state never would.
    private var granted by mutableStateOf(false)
    private var optimized by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        readSystemState()
        // Re-assert the schedule every time the app is opened. If the system
        // dropped the periodic jobs, this puts them back.
        RefreshWorker.schedulePeriodic(this)
        WxRefreshWorker.schedulePeriodic(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readSystemState()
        RefreshWorker.schedulePeriodic(this)
        WxRefreshWorker.schedulePeriodic(this)
        setContent { MaterialTheme { Screen() } }
    }

    private fun readSystemState() {
        granted = LocationSource.hasPermission(this)
        optimized = Diagnostics.isBatteryOptimized(this)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Screen() {
        val activity = this
        val scope = rememberCoroutineScope()
        val planeDiag by Diagnostics.report(activity, "plane")
            .collectAsState(initial = Diagnostics.Report(0, 0, "", 0))
        val wxDiag by Diagnostics.report(activity, "weather")
            .collectAsState(initial = Diagnostics.Report(0, 0, "", 0))
        val look by AppSettings.appearanceFlow(activity)
            .collectAsState(initial = AppSettings.Appearance())

        fun redrawWidgets() = scope.launch {
            NearestPlaneWidget().updateAll(activity)
            WeatherWidget().updateAll(activity)
        }

        val requestPermissions = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            granted = LocationSource.hasPermission(activity)
            if (granted) {
                RefreshWorker.refreshNow(activity)
                WxRefreshWorker.refreshNow(activity)
            }
        }

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("Nearest Plane", style = MaterialTheme.typography.headlineMedium)

                SectionCard("Location") {
                    if (granted) {
                        Text("Location access is on.")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Text(
                                "For the widgets to keep updating with the screen off, set " +
                                    "location to \"Allow all the time\" in app settings. Android " +
                                    "won't let an app request that from a dialog.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(onClick = { openAppSettings() }) {
                                Text("Open app settings")
                            }
                        }
                    } else {
                        Text("Both widgets need your location to work out what's closest.")
                        Button(onClick = {
                            requestPermissions.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }) { Text("Grant location access") }
                    }
                }

                SectionCard("Background") {
                    Text("Applies to both widgets.", style = MaterialTheme.typography.bodySmall)
                    WidgetBackground.entries.forEach { option ->
                        WideOption(
                            title = option.label,
                            subtitle = option.blurb,
                            selected = look.background == option,
                            onSelect = {
                                scope.launch {
                                    AppSettings.setBackground(activity, option)
                                    redrawWidgets()
                                }
                            }
                        )
                    }
                }

                SectionCard("Text colour") {
                    Text(
                        "Automatic follows your theme on Material You, and goes white on " +
                            "Scrim or Transparent. Pick a fixed colour if your wallpaper " +
                            "fights with it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetTextColor.entries.forEach { option ->
                            FilterChip(
                                selected = look.textColor == option,
                                onClick = {
                                    scope.launch {
                                        AppSettings.setTextColor(activity, option)
                                        redrawWidgets()
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }

                SectionCard("Text size") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetTextScale.entries.forEach { option ->
                            FilterChip(
                                selected = look.textScale == option,
                                onClick = {
                                    scope.launch {
                                        AppSettings.setTextScale(activity, option)
                                        redrawWidgets()
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Text(
                        "Larger text may clip on a small tile — drag the widget's edge to " +
                            "resize it if that happens.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (optimized) {
                    SectionCard("Background refresh is restricted") {
                        Text(
                            "Android is holding this app under battery optimisation. That " +
                                "defers the widgets' scheduled updates indefinitely, which is " +
                                "why they only refresh when you open the app.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { requestBatteryExemption() }) {
                            Text("Allow background updates")
                        }
                        Text(
                            "On Samsung and Xiaomi you may also need Settings \u2192 Apps \u2192 " +
                                "Nearest Plane \u2192 Battery \u2192 Unrestricted.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                SectionCard("Diagnostics") {
                    Text(
                        "Planes: last run ${Diagnostics.timeAgo(planeDiag.lastRun)}, " +
                            "last success ${Diagnostics.timeAgo(planeDiag.lastOk)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (planeDiag.lastError.isNotBlank()) {
                        Text(
                            "Planes error at ${Diagnostics.clockTime(planeDiag.lastErrorAt)}: " +
                                planeDiag.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "Weather: last run ${Diagnostics.timeAgo(wxDiag.lastRun)}, " +
                            "last success ${Diagnostics.timeAgo(wxDiag.lastOk)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (wxDiag.lastError.isNotBlank()) {
                        Text(
                            "Weather error at ${Diagnostics.clockTime(wxDiag.lastErrorAt)}: " +
                                wxDiag.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "If \"last run\" is hours old, the system isn't running the job at all " +
                            "\u2014 that's battery optimisation, not a bug in the fetch.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                SectionCard("Refresh now") {
                    Text(
                        "Background updates run every 15 min (planes) and 30 min (weather).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { RefreshWorker.refreshNow(activity) }) {
                            Text("Planes")
                        }
                        OutlinedButton(onClick = { WxRefreshWorker.refreshNow(activity) }) {
                            Text("Weather")
                        }
                    }
                }

                Text(
                    "Add the tiles by long-pressing your home screen, tapping Widgets, then " +
                        "finding Nearest Plane. Two tiles are listed.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    private fun SectionCard(title: String, content: @Composable () -> Unit) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                content()
            }
        }
    }

    @Composable
    private fun WideOption(
        title: String,
        subtitle: String,
        selected: Boolean,
        onSelect: () -> Unit
    ) {
        val modifier = Modifier.fillMaxWidth()
        val body: @Composable () -> Unit = {
            Column {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selected) Button(onClick = onSelect, modifier = modifier) { body() }
        else OutlinedButton(onClick = onSelect, modifier = modifier) { body() }
    }

    /**
     * Opens the system dialog that exempts this app from Doze. Without the
     * exemption, WorkManager's scheduled jobs are deferred for hours.
     */
    private fun requestBatteryExemption() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            // Some OEM builds block the direct request; fall back to the list.
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}
