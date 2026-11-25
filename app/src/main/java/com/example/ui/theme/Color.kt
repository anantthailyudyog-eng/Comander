package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.HudMode

val ObsidianVoid = Color(0xFF03060C)
val ObsidianSurface = Color(0xFF070D18)
val ObsidianPanel = Color(0xFF0B1424)
val ObsidianGlass = Color(0xD90E192D)
val ObsidianElevated = Color(0xFF13223B)

val NeonCyan = Color(0xFF00F3FF)
val ElectricBlue = Color(0xFF0088FF)
val StealthSky = Color(0xFF38BDF8)
val CodingIndigo = Color(0xFF6366F1)
val AmberAlert = Color(0xFFFFB000)
val CrimsonAlert = Color(0xFFFF3B5C)
val EmeraldNominal = Color(0xFF00E676)

val HudTextPrimary = Color(0xFFE6F4FF)
val HudTextSecondary = Color(0xFF8AA4C0)
val HudTextMuted = Color(0xFF526A85)
val HudOutline = Color(0x3300F3FF)
val HudOutlineStrong = Color(0x7300F3FF)

fun HudMode.accentColor(): Color = when (this) {
    HudMode.STANDARD -> NeonCyan
    HudMode.CODING -> ElectricBlue
    HudMode.FOCUS -> StealthSky
    HudMode.DIAGNOSTIC -> AmberAlert
}

fun HudMode.secondaryAccent(): Color = when (this) {
    HudMode.STANDARD -> ElectricBlue
    HudMode.CODING -> CodingIndigo
    HudMode.FOCUS -> Color(0xFF0EA5E9)
    HudMode.DIAGNOSTIC -> CrimsonAlert
}
