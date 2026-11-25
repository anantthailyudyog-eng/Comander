package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AttachmentItem
import com.example.data.ChatMessage
import com.example.hardware.VoiceCaptureState
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid

@Composable
fun CenterNeuralCore(
    messages: List<ChatMessage>,
    isProcessing: Boolean,
    voiceCaptureState: VoiceCaptureState,
    waveformBands: List<Float>,
    partialTranscript: String,
    wakeListenerEnabled: Boolean,
    attachments: List<AttachmentItem>,
    liveVisionBitmap: Bitmap?,
    screenLinkActive: Boolean,
    continuousWatchEnabled: Boolean,
    onArcReactorClick: () -> Unit,
    onToggleWakeListener: (Boolean) -> Unit,
    onSendCommand: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAnalyzeLiveFrame: () -> Unit,
    onToggleContinuousWatch: (Boolean) -> Unit,
    onStopLiveVision: () -> Unit,
    onCopyToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Arc-Reactor Orb + Wake Listener + Live Vision PiP
        HudGlassCard(accentColor = accent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArcReactorOrb(
                    captureState = voiceCaptureState,
                    isProcessing = isProcessing,
                    waveformBands = waveformBands,
                    onClick = onArcReactorClick,
                    orbSize = 112.dp,
                    modifier = Modifier.width(126.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = null,
                                tint = if (wakeListenerEnabled) EmeraldNominal else accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.wake_listener_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = HudTextPrimary
                            )
                        }
                        Switch(
                            checked = wakeListenerEnabled,
                            onCheckedChange = onToggleWakeListener,
                            modifier = Modifier.testTag("wake_listener_switch")
                        )
                    }

                    val statusLine = when {
                        partialTranscript.isNotBlank() -> "\"$partialTranscript\""
                        isProcessing -> "SYNTHESIZING NEURAL DIRECTIVE…"
                        voiceCaptureState == VoiceCaptureState.COMMAND_LISTENING -> "CAPTURING COMMAND AUDIO…"
                        voiceCaptureState == VoiceCaptureState.WAKE_LISTENING -> "PASSIVE WAKE GUARD: SAY \"JARVIS\"…"
                        voiceCaptureState == VoiceCaptureState.SPEAKING -> "JARVIS VOCAL TELEMETRY ACTIVE"
                        else -> "TAP ARC-REACTOR FOR VOICE OR TYPE BELOW"
                    }
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (partialTranscript.isNotBlank()) EmeraldNominal else accent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Quick suggestion pills
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "5 min focus timer",
                            "Mera daily plan banao",
                            "Draft status email"
                        ).forEach { suggestion ->
                            AssistChip(
                                onClick = { onSendCommand(suggestion) },
                                label = {
                                    Text(suggestion, style = MaterialTheme.typography.labelSmall)
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = ObsidianElevated,
                                    labelColor = HudTextPrimary
                                ),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            }

            // Live Screen Link / Camera PiP Monitor
            if (liveVisionBitmap != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = ObsidianVoid,
                    border = BorderStroke(1.dp, EmeraldNominal.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            bitmap = liveVisionBitmap.asImageBitmap(),
                            contentDescription = "Live Vision Feed",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 96.dp, height = 64.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (screenLinkActive) "LIVE SCREEN LINK MONITOR" else "OPTICAL SCAN BUFFER",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNominal
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = false,
                                    onClick = onAnalyzeLiveFrame,
                                    leadingIcon = {
                                        Icon(Icons.Default.RemoveRedEye, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text("Scan Frame", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = continuousWatchEnabled,
                                    onClick = { onToggleContinuousWatch(!continuousWatchEnabled) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text("Watch", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberAlert.copy(alpha = 0.22f),
                                        selectedLabelColor = AmberAlert
                                    )
                                )
                            }
                        }
                        IconButton(
                            onClick = onStopLiveVision,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Vision PiP", tint = CrimsonAlert, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 2. Live Neural Stream Terminal
        HudGlassCard(
            accentColor = accent,
            modifier = Modifier.weight(1f)
        ) {
            HudSectionHeader(
                title = "Neural Command Terminal",
                badgeText = if (isProcessing) "PROCESSING" else "LIVE",
                accentColor = if (isProcessing) AmberAlert else accent
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    RichTerminalMessageCard(
                        message = msg,
                        onCopySuccess = onCopyToast
                    )
                }

                if (isProcessing) {
                    item(key = "thinking_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = accent,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "J.A.R.V.I.S. is evaluating tools and neural context…",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                        }
                    }
                }
            }
        }

        // 3. Multimodal Attachment Dropzone + Command Input Bar
        HudGlassCard(accentColor = accent) {
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    attachments.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianElevated,
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.bitmap != null) Icons.Default.Image else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove attachment",
                                    tint = CrimsonAlert,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRemoveAttachment(item.id) }
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DROPZONE:",
                        style = MaterialTheme.typography.labelSmall,
                        color = HudTextSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ObsidianElevated,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clickable(onClick = onPickImage)
                            .testTag("attach_image_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Photo", style = MaterialTheme.typography.labelSmall, color = HudTextPrimary)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ObsidianElevated,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clickable(onClick = onPickDocument)
                            .testTag("attach_file_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF / Code", style = MaterialTheme.typography.labelSmall, color = HudTextPrimary)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.command_input_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = HudTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() || attachments.isNotEmpty()) {
                            onSendCommand(inputText)
                            inputText = ""
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = accent.copy(alpha = 0.35f),
                        focusedTextColor = HudTextPrimary,
                        unfocusedTextColor = HudTextPrimary,
                        cursorColor = accent,
                        focusedContainerColor = ObsidianVoid.copy(alpha = 0.7f),
                        unfocusedContainerColor = ObsidianVoid.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field")
                )

                IconButton(
                    onClick = onArcReactorClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (voiceCaptureState == VoiceCaptureState.COMMAND_LISTENING) {
                                EmeraldNominal.copy(alpha = 0.22f)
                            } else {
                                ObsidianElevated
                            },
                            shape = CircleShape
                        )
                        .testTag("mic_command_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.cd_voice_capture),
                        tint = if (voiceCaptureState == VoiceCaptureState.COMMAND_LISTENING) EmeraldNominal else accent
                    )
                }

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() || attachments.isNotEmpty()) {
                            onSendCommand(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !isProcessing && (inputText.isNotBlank() || attachments.isNotEmpty()),
                    modifier = Modifier
                        .size(48.dp)
                        .background(accent.copy(alpha = 0.2f), CircleShape)
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send_command),
                        tint = accent
                    )
                }
            }
        }
    }
}
