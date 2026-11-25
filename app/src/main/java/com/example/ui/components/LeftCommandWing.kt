package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CustomMacro
import com.example.data.HudMode
import com.example.data.TelemetrySnapshot
import com.example.data.UiState
import com.example.data.VoiceSettings
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeftCommandWing(
    hudMode: HudMode,
    telemetry: TelemetrySnapshot,
    voiceSettings: VoiceSettings,
    screenLinkActive: Boolean,
    macrosState: UiState<List<CustomMacro>>,
    onRunDailyBriefing: () -> Unit,
    onQuickMacroDeepWork: () -> Unit,
    onQuickMacroCodeDebugger: () -> Unit,
    onQuickMacroScreenScan: () -> Unit,
    onToggleScreenLink: () -> Unit,
    onOpenCameraScanner: () -> Unit,
    onOpenMacroBuilder: () -> Unit,
    onRunCustomMacro: (CustomMacro) -> Unit,
    onDeleteCustomMacro: (String) -> Unit,
    onUpdateVoiceSettings: (pitch: Float?, rate: Float?, muted: Boolean?, locale: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Quick-Launch Command Hub
        HudGlassCard(accentColor = accent) {
            HudSectionHeader(title = "Command Hub", badgeText = hudMode.shortTag, accentColor = accent)
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickMacroTile(
                        title = "Morning Briefing",
                        subtitle = "Daily Intel",
                        icon = Icons.Default.WbSunny,
                        accent = AmberAlert,
                        onClick = onRunDailyBriefing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("macro_daily_briefing")
                    )
                    QuickMacroTile(
                        title = "Deep Work",
                        subtitle = "Stealth 25m",
                        icon = Icons.Default.SelfImprovement,
                        accent = accent,
                        onClick = onQuickMacroDeepWork,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("macro_deep_work")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickMacroTile(
                        title = "Code Debugger",
                        subtitle = "Dev Matrix",
                        icon = Icons.Default.BugReport,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onQuickMacroCodeDebugger,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("macro_code_debugger")
                    )
                    QuickMacroTile(
                        title = "Screen Scan",
                        subtitle = "Visual AI",
                        icon = Icons.Default.Monitor,
                        accent = EmeraldNominal,
                        onClick = onQuickMacroScreenScan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("macro_screen_scan")
                    )
                }
            }
        }

        // 2. Multimodal Optical & Screen Link
        HudGlassCard(accentColor = accent) {
            HudSectionHeader(
                title = "Live Vision Links",
                badgeText = if (screenLinkActive) "STREAMING" else "STANDBY",
                accentColor = if (screenLinkActive) EmeraldNominal else accent
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleScreenLink,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (screenLinkActive) CrimsonAlert.copy(alpha = 0.22f) else accent.copy(alpha = 0.18f),
                        contentColor = if (screenLinkActive) CrimsonAlert else accent
                    ),
                    border = BorderStroke(1.dp, if (screenLinkActive) CrimsonAlert else accent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("screen_link_button")
                ) {
                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (screenLinkActive) "Stop Link" else stringResource(R.string.screen_link_label),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenCameraScanner,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("camera_scan_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Optical Scan",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Custom Macro Builder
        HudGlassCard(accentColor = accent) {
            HudSectionHeader(
                title = "Custom Macros",
                accentColor = accent,
                trailing = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable(onClick = onOpenMacroBuilder)
                            .testTag("new_macro_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create Macro", tint = accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FORGE", style = MaterialTheme.typography.labelSmall, color = accent)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (macrosState) {
                is UiState.Loading -> {
                    Text("Synchronizing macro routines…", style = MaterialTheme.typography.bodySmall, color = HudTextSecondary)
                }
                is UiState.Error -> {
                    Text(macrosState.message, style = MaterialTheme.typography.bodySmall, color = CrimsonAlert)
                }
                is UiState.Success -> {
                    val macros = macrosState.data
                    if (macros.isEmpty()) {
                        Text(
                            text = "No custom macros forged yet. Tap FORGE to build 1-click or voice-triggered routines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HudTextSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            macros.forEach { macro ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = ObsidianElevated.copy(alpha = 0.72f),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clickable { onRunCustomMacro(macro) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(macro.name, style = MaterialTheme.typography.bodyMedium, color = HudTextPrimary)
                                            if (macro.voiceTrigger.isNotBlank()) {
                                                Text(
                                                    text = "Voice: \"${macro.voiceTrigger}\"",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accent
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onRunCustomMacro(macro) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Run Macro", tint = EmeraldNominal, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { onDeleteCustomMacro(macro.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Macro", tint = HudTextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. System Diagnostics & Voice Telemetry Calibration
        HudGlassCard(accentColor = accent) {
            HudSectionHeader(title = "Diagnostics & Voice", accentColor = accent)
            Spacer(modifier = Modifier.height(10.dp))

            DiagnosticMeter("NEURAL ENGINE LOAD", telemetry.neuralLoadPercent / 100f, "${telemetry.neuralLoadPercent}%", accent)
            Spacer(modifier = Modifier.height(6.dp))
            DiagnosticMeter("JVM MEMORY ALLOC", telemetry.ramUsagePercent / 100f, "${telemetry.ramUsagePercent}%", EmeraldNominal)
            Spacer(modifier = Modifier.height(6.dp))
            DiagnosticMeter("POWER CELL", telemetry.batteryPercent / 100f, "${telemetry.batteryPercent}%", if (telemetry.batteryPercent < 20) CrimsonAlert else AmberAlert)

            Spacer(modifier = Modifier.height(12.dp))
            Text("VOICE LOCALE (STT / TTS)", style = MaterialTheme.typography.labelSmall, color = HudTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("en-IN" to "EN-IN / Hinglish", "hi-IN" to "Hindi (HI-IN)", "en-US" to "EN-US").forEach { (tag, label) ->
                    FilterChip(
                        selected = voiceSettings.speechLocaleTag == tag,
                        onClick = { onUpdateVoiceSettings(null, null, null, tag) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.22f),
                            selectedLabelColor = accent
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (voiceSettings.ttsMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (voiceSettings.ttsMuted) CrimsonAlert else accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Voice Synthesis Output", style = MaterialTheme.typography.bodySmall, color = HudTextPrimary)
                }
                Switch(
                    checked = !voiceSettings.ttsMuted,
                    onCheckedChange = { enabled -> onUpdateVoiceSettings(null, null, !enabled, null) }
                )
            }

            Text(
                text = "PITCH: ${String.format(java.util.Locale.US, "%.2f", voiceSettings.ttsPitch)}x",
                style = MaterialTheme.typography.labelSmall,
                color = HudTextSecondary
            )
            Slider(
                value = voiceSettings.ttsPitch,
                onValueChange = { onUpdateVoiceSettings(it, null, null, null) },
                valueRange = 0.6f..1.5f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )

            Text(
                text = "TEMPO: ${String.format(java.util.Locale.US, "%.2f", voiceSettings.ttsRate)}x",
                style = MaterialTheme.typography.labelSmall,
                color = HudTextSecondary
            )
            Slider(
                value = voiceSettings.ttsRate,
                onValueChange = { onUpdateVoiceSettings(null, it, null, null) },
                valueRange = 0.7f..1.6f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )
        }
    }
}

@Composable
private fun QuickMacroTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = ObsidianElevated.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = HudTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
        }
    }
}

@Composable
private fun DiagnosticMeter(
    label: String,
    progress: Float,
    valueText: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = HudTextSecondary)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = color)
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = color,
            trackColor = ObsidianVoid
        )
    }
}
