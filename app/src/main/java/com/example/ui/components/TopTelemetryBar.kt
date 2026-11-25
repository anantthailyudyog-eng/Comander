package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.HudMode
import com.example.data.TelemetrySnapshot
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.ObsidianPanel

@Composable
fun TopTelemetryBar(
    telemetry: TelemetrySnapshot,
    hudMode: HudMode,
    onSelectMode: (HudMode) -> Unit,
    onEmergencyStop: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    var modeMenuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ObsidianPanel.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        shape = MaterialTheme.shapes.medium
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val compactBar = maxWidth < 680.dp

            val brandBlock: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.jarvis_designation),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent
                        )
                        Text(
                            text = "${telemetry.clockTime}  •  ${telemetry.dateLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextSecondary
                        )
                    }
                }
            }

            val telemetryPills: @Composable (Modifier) -> Unit = { rowMod ->
                Row(
                    modifier = rowMod.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TelemetryChip(
                        icon = Icons.Default.Memory,
                        text = "NEURAL ${telemetry.neuralLoadPercent}%",
                        tint = if (telemetry.neuralLoadPercent > 70) AmberAlert else accent
                    )
                    TelemetryChip(
                        icon = Icons.Default.Speed,
                        text = if (telemetry.apiLatencyMs > 0) "${telemetry.apiLatencyMs}ms" else "READY",
                        tint = EmeraldNominal
                    )
                    TelemetryChip(
                        icon = if (telemetry.batteryCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        text = "${telemetry.batteryPercent}%",
                        tint = if (telemetry.batteryPercent <= 20) CrimsonAlert else HudTextPrimary
                    )
                    TelemetryChip(
                        icon = if (telemetry.networkOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                        text = telemetry.networkLabel,
                        tint = if (telemetry.networkOnline) EmeraldNominal else CrimsonAlert
                    )
                }
            }

            val modeBadge: @Composable () -> Unit = {
                Box {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable { modeMenuOpen = true }
                            .testTag("hud_mode_badge")
                    ) {
                        Text(
                            text = "MODE: ${hudMode.shortTag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = modeMenuOpen,
                        onDismissRequest = { modeMenuOpen = false }
                    ) {
                        HudMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                onClick = {
                                    modeMenuOpen = false
                                    onSelectMode(mode)
                                }
                            )
                        }
                    }
                }
            }

            val actionControls: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modeBadge()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CrimsonAlert.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .clickable(onClick = onEmergencyStop)
                            .testTag("emergency_stop_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = stringResource(R.string.cd_emergency_stop),
                                tint = CrimsonAlert,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.emergency_stop),
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonAlert
                            )
                        }
                    }

                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.cd_sign_out),
                            tint = HudTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (compactBar) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        brandBlock()
                        actionControls()
                    }
                    telemetryPills(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    brandBlock()
                    telemetryPills(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    )
                    actionControls()
                }
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}
