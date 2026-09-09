package com.connor.nearestplane.wx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.connor.nearestplane.LocationSource
import com.connor.nearestplane.AppSettings
import com.connor.nearestplane.WidgetPalette
import kotlinx.coroutines.launch

/**
 * Opened by tapping the weather tile. Fetches fresh rather than reading the
 * widget's cached state — if you've bothered to open it, you want current data.
 */
class WeatherDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tapping the tile opens this screen; refresh the tile behind it too,
        // so closing the screen doesn't leave a stale widget.
        WxRefreshWorker.refreshNow(this)
        setContent { MaterialTheme { Screen() } }
    }

    @Composable
    private fun Screen() {
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var metar by remember { mutableStateOf<Metar?>(null) }
        var taf by remember { mutableStateOf<Taf?>(null) }
        val scope = rememberCoroutineScope()

        suspend fun load() {
            loading = true
            error = null
            try {
                if (!LocationSource.hasPermission(this@WeatherDetailActivity)) {
                    error = "Location access is off. Grant it in the main app screen."
                    return
                }
                val fix = LocationSource.current(this@WeatherDetailActivity)
                if (fix == null) {
                    error = "No GPS fix yet. Step outside or try again in a moment."
                    return
                }
                val m = AviationWeatherClient.nearestMetar(fix.lat, fix.lon)
                if (m == null) {
                    error = "No reporting station within about 45 nm."
                    return
                }
                metar = m
                taf = try {
                    AviationWeatherClient.taf(m.stationId)
                } catch (_: Exception) {
                    null
                }
            } catch (e: Exception) {
                error = e.message ?: "Request failed."
            } finally {
                loading = false
            }
        }

        LaunchedEffect(Unit) { load() }

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val message = error
                val m = metar
                when {
                    loading -> CircularProgressIndicator()
                    message != null -> {
                        Text(message, style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { scope.launch { load() } }) { Text("Try again") }
                    }
                    m == null -> {
                        Text("No observation loaded.", style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { scope.launch { load() } }) { Text("Try again") }
                    }
                    else -> {
                        Text(
                            "${m.stationId} — ${m.flightCategory}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = WidgetPalette.categoryColor(m.flightCategory, AppSettings.Appearance())
                        )
                        m.siteName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }

                        Section("Raw METAR") { Mono(m.raw) }
                        Section("Decoded METAR") {
                            Text(m.decoded, style = MaterialTheme.typography.bodyMedium)
                        }

                        val t = taf
                        if (t == null || t.raw.isBlank()) {
                            Section("TAF") {
                                Text(
                                    "${m.stationId} doesn't issue a TAF. Only larger airports do.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            Section("Raw TAF") { Mono(t.raw) }
                            Section("Decoded TAF") {
                                Text(decodeTaf(t), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        OutlinedButton(onClick = {
                            scope.launch { load() }
                            WxRefreshWorker.refreshNow(this@WeatherDetailActivity)
                        }) { Text("Refresh") }
                    }
                }
            }
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) { content() }
            }
        }
    }

    @Composable
    private fun Mono(text: String) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
    }

    private fun decodeTaf(taf: Taf): String = buildString {
        taf.issuedEpoch?.let { append("Issued ${zulu(it)}\n\n") }
        if (taf.periods.isEmpty()) {
            append("No forecast periods parsed — see the raw text above.")
            return@buildString
        }
        taf.periods.forEachIndexed { i, p ->
            if (i > 0) append("\n\n")
            append(p.changeType?.uppercase() ?: "FROM")
            append(" ${zulu(p.fromEpoch)} – ${zulu(p.toEpoch)}\n")
            append("  ${p.summary.ifBlank { "no significant change" }}")
        }
    }

    private fun zulu(epochSeconds: Long?): String {
        if (epochSeconds == null) return "—"
        val fmt = java.text.SimpleDateFormat("dd HH:mm'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(epochSeconds * 1000))
    }
}
