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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.connor.nearestplane.wx.AviationWeatherClient
import com.connor.nearestplane.wx.WeatherWidget
import com.connor.nearestplane.wx.WxRefreshWorker
import kotlinx.coroutines.launch
import java.util.Locale

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
        val filter by AppSettings.planeFilterFlow(activity)
            .collectAsState(initial = AppSettings.PlaneFilter())
        val mode by AppSettings.locationModeFlow(activity)
            .collectAsState(initial = LocationMode.DEVICE)
        val pin by AppSettings.pinFlow(activity).collectAsState(initial = null)

        var code by remember { mutableStateOf("") }
        var pinBusy by remember { mutableStateOf(false) }
        var pinError by remember { mutableStateOf<String?>(null) }

        fun redrawWidgets() = scope.launch {
            NearestPlaneWidget().updateAll(activity)
            WeatherWidget().updateAll(activity)
        }

        // Settings that change *what is fetched* need a refetch, not a redraw.
        fun refetch() {
            RefreshWorker.refreshNow(activity)
            WxRefreshWorker.refreshNow(activity)
        }

        val requestPermissions = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            granted = LocationSource.hasPermission(activity)
            if (granted) refetch()
        }

        fun pinAirport() = scope.launch {
            pinBusy = true
            pinError = null
            try {
                val found = AviationWeatherClient.airport(code)
                if (found == null) {
                    pinError = "No airport found for \"${code.trim().uppercase()}\"."
                } else {
                    AppSettings.savePin(activity, found.id, found.lat, found.lon)
                    code = ""
                    refetch()
                }
            } catch (e: Exception) {
                pinError = e.message ?: "Lookup failed."
            } finally {
                pinBusy = false
            }
        }

        fun pinHere() = scope.launch {
            pinBusy = true
            pinError = null
            try {
                // deviceFix, not current: a pin is already in effect, and asking
                // for "the current position" would just hand back the pin.
                val fix = LocationSource.deviceFix(activity)
                if (fix == null) {
                    pinError = "No position available yet. Try again in a moment."
                } else {
                    AppSettings.savePin(activity, "Pinned position", fix.lat, fix.lon)
                    refetch()
                }
            } catch (e: Exception) {
                pinError = e.message ?: "Couldn't read your position."
            } finally {
                pinBusy = false
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
                    Text(
                        "Both widgets need a position to work out what's closest.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LocationMode.entries.forEach { option ->
                        WideOption(
                            title = option.label,
                            subtitle = option.blurb,
                            selected = mode == option,
                            onSelect = {
                                scope.launch {
                                    AppSettings.setLocationMode(activity, option)
                                    refetch()
                                }
                            }
                        )
                    }

                    HorizontalDivider()

                    if (mode == LocationMode.DEVICE) {
                        if (granted) {
                            Text("Location access is on.")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                Text(
                                    "For the widgets to keep updating with the screen off, set " +
                                        "location to \"Allow all the time\" in app settings. " +
                                        "Android won't let an app request that from a dialog.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(onClick = { openAppSettings() }) {
                                    Text("Open app settings")
                                }
                            }
                        } else {
                            Text("This mode needs location access.")
                            Button(onClick = {
                                requestPermissions.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }) { Text("Grant location access") }
                        }
                    } else {
                        val current = pin
                        if (current == null) {
                            Text("Nowhere pinned yet — the widgets have nothing to work from.")
                        } else {
                            Text("Pinned to ${current.label}", fontWeight = FontWeight.Medium)
                            Text(
                                "%.4f, %.4f".format(Locale.US, current.lat, current.lon),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it; pinError = null },
                            label = { Text("Airport code") },
                            placeholder = { Text("KJFK, EGLL, YSSY…") },
                            singleLine = true,
                            enabled = !pinBusy,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pinAirport() },
                                enabled = !pinBusy && code.isNotBlank()
                            ) { Text("Pin airport") }
                            OutlinedButton(
                                onClick = {
                                    if (granted) {
                                        pinHere()
                                    } else {
                                        requestPermissions.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            )
                                        )
                                    }
                                },
                                enabled = !pinBusy
                            ) { Text("Pin where I am") }
                        }
                        pinError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            "\"Pin where I am\" needs location access once, and then never " +
                                "again — no background permission, and nothing to grant in " +
                                "Settings by hand.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                SectionCard("Plane search") {
                    Text(
                        "Applies to the Nearest Plane tile.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Range", fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchRadius.entries.forEach { option ->
                            FilterChip(
                                selected = filter.radius == option,
                                onClick = {
                                    scope.launch {
                                        AppSettings.setRadius(activity, option)
                                        refetch()
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Text("Ceiling", fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AltitudeCeiling.entries.forEach { option ->
                            FilterChip(
                                selected = filter.ceiling == option,
                                onClick = {
                                    scope.launch {
                                        AppSettings.setCeiling(activity, option)
                                        refetch()
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Text(
                        "A ceiling is what stops the tile showing the same airliner at " +
                            "FL380 forty miles away every time. If you live under an airway, " +
                            "it's the difference between a widget and wallpaper.",
                        style = MaterialTheme.typography.bodySmall
                    )
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

                SectionCard("Text size and weight") {
                    Text("Size", fontWeight = FontWeight.Medium)
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
                    Text("Weight", fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetTextWeight.entries.forEach { option ->
                            FilterChip(
                                selected = look.textWeight == option,
                                onClick = {
                                    scope.launch {
                                        AppSettings.setTextWeight(activity, option)
                                        redrawWidgets()
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Text(
                        "Bold is the default, and it's the other half of legibility over a " +
                            "photo — Glance can't draw a text shadow, so weight and colour " +
                            "are the only levers there are.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Larger text may clip on a small tile — drag the widget's edge to " +
                            "resize it if that happens.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                SectionCard("Temperature") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TemperatureUnit.entries.forEach { option ->
                            FilterChip(
                                selected = look.temperature == option,
                                onClick = {
                                    scope.launch {
                                        AppSettings.setTemperature(activity, option)
                                        // The summary line is built when the METAR
                                        // is fetched, so this needs a refetch.
                                        WxRefreshWorker.refreshNow(activity)
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Text(
                        "The METAR tile only. Raw METARs are always in Celsius, and the " +
                            "detail screen shows both regardless.",
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
                            "On Samsung and Xiaomi you may also need Settings → Apps → " +
                                "Nearest Plane → Battery → Unrestricted.",
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
                            "— that's battery optimisation, not a bug in the fetch.",
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
