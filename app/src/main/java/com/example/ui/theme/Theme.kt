package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.HudMode

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun JarvisOsTheme(
    hudMode: HudMode = HudMode.STANDARD,
    content: @Composable () -> Unit
) {
    val primaryAccent = hudMode.accentColor()
    val secondaryAccent = hudMode.secondaryAccent()

    val colorScheme = darkColorScheme(
        primary = primaryAccent,
        onPrimary = ObsidianVoid,
        primaryContainer = primaryAccent.copy(alpha = 0.16f),
        onPrimaryContainer = primaryAccent,
        secondary = secondaryAccent,
        onSecondary = Color.White,
        secondaryContainer = secondaryAccent.copy(alpha = 0.18f),
        onSecondaryContainer = HudTextPrimary,
        tertiary = AmberAlert,
        onTertiary = ObsidianVoid,
        tertiaryContainer = AmberAlert.copy(alpha = 0.16f),
        onTertiaryContainer = AmberAlert,
        background = ObsidianVoid,
        onBackground = HudTextPrimary,
        surface = ObsidianSurface,
        onSurface = HudTextPrimary,
        surfaceVariant = ObsidianPanel,
        onSurfaceVariant = HudTextSecondary,
        surfaceContainerLowest = ObsidianVoid,
        surfaceContainerLow = ObsidianSurface,
        surfaceContainer = ObsidianPanel,
        surfaceContainerHigh = ObsidianElevated,
        outline = primaryAccent.copy(alpha = 0.30f),
        outlineVariant = primaryAccent.copy(alpha = 0.14f),
        error = CrimsonAlert,
        onError = Color.White,
        errorContainer = CrimsonAlert.copy(alpha = 0.18f),
        onErrorContainer = CrimsonAlert
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
