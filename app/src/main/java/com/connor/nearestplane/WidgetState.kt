package com.connor.nearestplane

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The worker writes these; the widget reads them. Glance persists the values
 * per widget instance, so tiles survive reboots and process death.
 */
object WidgetState {
    val TITLE = stringPreferencesKey("title")            // callsign / tail
    val AIRLINE = stringPreferencesKey("airline")
    val TYPE_NAME = stringPreferencesKey("type_name")    // "Boeing 747-8"
    val ROUTE = stringPreferencesKey("route")            // "KEWR → KLAX"
    val TIMING = stringPreferencesKey("timing")          // "~1h20m out · 2h14m to go"
    val POSITION = stringPreferencesKey("position")      // "3.2 nm NNE"
    val DETAIL = stringPreferencesKey("detail")          // "FL350 · 452 kts"
    val EMERGENCY = stringPreferencesKey("emergency")
    val STATUS = stringPreferencesKey("status")          // ok | empty | no_permission | error
    val UPDATED_AT = longPreferencesKey("updated_at")
    val STALE_NOTE = stringPreferencesKey("stale_note")  // set when a refresh failed
}
