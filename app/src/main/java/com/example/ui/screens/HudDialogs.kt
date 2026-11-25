package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.WriterDocType
import com.example.ui.WriterEditorState
import com.example.ui.components.HudGlassCard
import com.example.ui.components.HudSectionHeader
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianVoid

@Composable
fun MacroBuilderOverlay(
    onCreateMacro: (name: String, voiceTrigger: String, promptCommand: String, iconKey: String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val accent = MaterialTheme.colorScheme.primary

    var name by rememberSaveable { mutableStateOf("") }
    var voiceTrigger by rememberSaveable { mutableStateOf("") }
    var promptCommand by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid.copy(alpha = 0.88f))
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        HudGlassCard(
            accentColor = accent,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            HudSectionHeader(
                title = "Custom Macro Builder",
                accentColor = accent,
                trailing = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close Macro Builder", tint = HudTextPrimary)
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Macro Name") },
                placeholder = { Text("e.g., Standup Prep") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("macro_name_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = voiceTrigger,
                onValueChange = { voiceTrigger = it },
                label = { Text("Voice Trigger Phrase (Optional)") },
                placeholder = { Text("e.g., prepare standup") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("macro_voice_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = promptCommand,
                onValueChange = { promptCommand = it },
                label = { Text("Autonomous Command Prompt") },
                placeholder = { Text("e.g., Switch to Coding/Dev Mode and summarize my top 3 tasks.") },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("macro_prompt_input")
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (name.isNotBlank() && promptCommand.isNotBlank()) {
                            onCreateMacro(name, voiceTrigger, promptCommand, "bolt")
                        }
                    },
                    enabled = name.isNotBlank() && promptCommand.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = ObsidianVoid),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_macro_button")
                ) {
                    Text("Forge Macro")
                }
            }
        }
    }
}

@Composable
fun WriterPadOverlay(
    state: WriterEditorState,
    onUpdateDraft: (title: String?, docType: WriterDocType?, tone: String?, content: String?) -> Unit,
    onGenerateWithAi: (title: String, docType: WriterDocType, tone: String, prompt: String) -> Unit,
    onSaveDocument: () -> Unit,
    onCopyToast: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val accent = MaterialTheme.colorScheme.primary
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var aiPrompt by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid.copy(alpha = 0.94f))
            .safeDrawingPadding()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        HudGlassCard(
            accentColor = accent,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
        ) {
            HudSectionHeader(
                title = "AI Studio Writer & Code Pad",
                badgeText = if (state.generating) "STREAMING" else state.docType.label,
                accentColor = accent,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(state.content))
                            onCopyToast()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy draft", tint = accent)
                        }
                        IconButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, state.title)
                                putExtra(Intent.EXTRA_TEXT, state.content)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Draft"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share draft", tint = accent)
                        }
                        IconButton(onClick = onClose, modifier = Modifier.testTag("close_writer_pad_button")) {
                            Icon(Icons.Default.Close, contentDescription = "Close Writer Pad", tint = HudTextPrimary)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { onUpdateDraft(it, null, null, null) },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = state.tone,
                        onValueChange = { onUpdateDraft(null, null, it, null) },
                        label = { Text("Tone / Language") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WriterDocType.entries.forEach { t ->
                        FilterChip(
                            selected = state.docType == t,
                            onClick = { onUpdateDraft(null, t, null, null) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // AI Stream Draft bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        placeholder = { Text("Ask JARVIS to draft or rewrite…", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("writer_ai_prompt_input")
                    )
                    Button(
                        onClick = {
                            if (aiPrompt.isNotBlank()) {
                                val title = state.title.ifBlank { aiPrompt.take(40) }
                                onGenerateWithAi(title, state.docType, state.tone, aiPrompt)
                            }
                        },
                        enabled = !state.generating && aiPrompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = ObsidianVoid),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (state.generating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ObsidianVoid, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Draft", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = state.content,
                    onValueChange = { onUpdateDraft(null, null, null, it) },
                    label = { Text("Editor Surface", color = HudTextSecondary) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, color = HudTextPrimary),
                    minLines = 12,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = accent.copy(alpha = 0.35f),
                        focusedContainerColor = ObsidianVoid,
                        unfocusedContainerColor = ObsidianVoid
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("writer_content_editor")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close")
                }
                Button(
                    onClick = onSaveDocument,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNominal, contentColor = ObsidianVoid),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("writer_save_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save to Vault", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
