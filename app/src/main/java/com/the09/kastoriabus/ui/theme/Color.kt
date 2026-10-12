package com.the09.kastoriabus.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand colours: the blue of Lake Orestiada, with orange and teal accents.
val LakeBlue = Color(0xFF1449C8)
val LakeBlueLight = Color(0xFF9DB6FF)
val SkyBlue = Color(0xFF1A9BE6)
val SunsetOrange = Color(0xFFFF7A1A)
val SunsetOrangeLight = Color(0xFFFFA463)
val SunsetPink = Color(0xFFF2457B)
val Teal = Color(0xFF00A58E)
val TealLight = Color(0xFF4FD8C0)
val FavoriteRed = Color(0xFFF23B4C)

/** The blue gradient behind the screen titles. */
val HeaderBrush = Brush.linearGradient(listOf(LakeBlue, SkyBlue))
val HeaderBrushDark = Brush.linearGradient(listOf(Color(0xFF0B2A80), Color(0xFF0B5E96)))

/** The orange gradient of the highlighted cards (favorite stops, next bus). */
val AccentBrush = Brush.linearGradient(listOf(SunsetOrange, SunsetPink))
