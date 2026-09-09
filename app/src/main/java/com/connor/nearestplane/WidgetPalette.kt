package com.connor.nearestplane

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import kotlin.math.roundToInt

/**
 * Glance has no text-shadow support, so readability over a transparent
 * background comes from colour choice alone.
 */
object WidgetPalette {

    @Composable
    fun background(a: AppSettings.Appearance): ColorProvider = when (a.background) {
        WidgetBackground.DYNAMIC -> GlanceTheme.colors.widgetBackground
        WidgetBackground.SCRIM -> ColorProvider(Color(0x8C000000))
        WidgetBackground.TRANSPARENT -> ColorProvider(Color.Transparent)
    }

    @Composable
    fun primaryText(a: AppSettings.Appearance): ColorProvider {
        a.textColor.argb?.let { return ColorProvider(Color(it)) }
        return when (a.background) {
            WidgetBackground.DYNAMIC -> GlanceTheme.colors.onSurface
            else -> ColorProvider(Color.White)
        }
    }

    @Composable
    fun secondaryText(a: AppSettings.Appearance): ColorProvider {
        a.textColor.argb?.let { return ColorProvider(Color(it).copy(alpha = 0.78f)) }
        return when (a.background) {
            WidgetBackground.DYNAMIC -> GlanceTheme.colors.onSurfaceVariant
            else -> ColorProvider(Color(0xCCFFFFFF))
        }
    }

    /** Base point size scaled by the user's text-size preference. */
    fun size(base: Int, a: AppSettings.Appearance): Int =
        (base * a.textScale.factor).roundToInt()

    /**
     * One step heavier when the user asks for bold. Glance offers only three
     * weights, so this saturates at Bold rather than quietly doing nothing.
     */
    fun weight(base: FontWeight, a: AppSettings.Appearance): FontWeight = when {
        a.textWeight != WidgetTextWeight.BOLD -> base
        base == FontWeight.Normal -> FontWeight.Medium
        else -> FontWeight.Bold
    }

    /**
     * VFR/MVFR/IFR/LIFR are a fixed aviation convention, so these never follow
     * the wallpaper or the text colour — but they brighten on dark backgrounds,
     * where the darker sectional shades disappear.
     */
    fun categoryColor(cat: String, a: AppSettings.Appearance): Color {
        val onDark = a.background != WidgetBackground.DYNAMIC
        return when (cat) {
            "VFR" -> if (onDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
            "MVFR" -> if (onDark) Color(0xFF64B5F6) else Color(0xFF1565C0)
            "IFR" -> if (onDark) Color(0xFFEF5350) else Color(0xFFC62828)
            "LIFR" -> if (onDark) Color(0xFFBA68C8) else Color(0xFF7B1FA2)
            else -> Color.Gray
        }
    }
}
