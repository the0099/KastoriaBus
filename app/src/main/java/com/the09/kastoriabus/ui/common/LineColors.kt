package com.the09.kastoriabus.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The API gives every Kastoria line the same grey (#787878), which makes them hard
 * to tell apart. So each line gets its own bright colour here. If the API ever sends
 * a real colour (anything but that grey), we use the API's colour instead.
 */
private const val API_DEFAULT_GREY = "#787878"

private val LINE_PALETTE = mapOf(
    "01" to Color(0xFFE53935), // red
    "02" to Color(0xFF1E88E5), // blue
    "03" to Color(0xFF43A047), // green
    "04" to Color(0xFFFB8C00), // orange
    "05" to Color(0xFF8E24AA), // purple
    "06" to Color(0xFF00ACC1), // cyan
    "07" to Color(0xFFF4511E), // deep orange
    "08" to Color(0xFF3949AB), // indigo
    "09" to Color(0xFFD81B60), // pink
    "10" to Color(0xFF558B2F), // olive green
    "11" to Color(0xFF6D4C41), // brown
    "13" to Color(0xFF00897B), // teal
    "14" to Color(0xFF9E9D24), // lime
    "15" to Color(0xFF5E35B1), // deep purple
    "16" to Color(0xFF039BE5), // light blue
    "17" to Color(0xFFEF6C00), // dark orange
    "53" to Color(0xFFAD1457), // raspberry
    "54" to Color(0xFF2E7D32), // forest green
)

/** The colour of a line's badge, route and timeline. */
fun lineColor(code: String, apiColor: String? = null): Color {
    if (apiColor != null && !apiColor.equals(API_DEFAULT_GREY, ignoreCase = true)) {
        return parseColor(apiColor, Color.Gray)
    }
    // A line we don't know yet still gets a stable colour, picked from its code.
    return LINE_PALETTE[code] ?: LINE_PALETTE.values.toList()[Math.floorMod(code.hashCode(), LINE_PALETTE.size)]
}

/** The same colour as "#RRGGBB" text, for the map. */
fun lineColorHex(code: String, apiColor: String? = null): String =
    String.format("#%06X", lineColor(code, apiColor).toArgb() and 0xFFFFFF)
