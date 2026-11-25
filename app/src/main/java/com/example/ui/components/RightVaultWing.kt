package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ActiveTimer
import com.example.data.MemoryNote
import com.example.data.PriorityTask
import com.example.data.TaskPriority
import com.example.data.UiState
import com.example.data.WriterDocument
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import java.util.Locale

@Composable
fun RightVaultWing(
    timers: List<ActiveTimer>,
    tasksState: UiState<List<PriorityTask>>,
    memoriesState: UiState<List<MemoryNote>>,
    documentsState: UiState<List<WriterDocument>>,
    onStartQuickTimer: (String, Int) -> Unit,
    onToggleTimer: (String) -> Unit,
    onDismissTimer: (String) -> Unit,
    onSilenceAlarms: () -> Unit,
    onAddTask: (String, TaskPriority, Int) -> Unit,
    onToggleTask: (PriorityTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    onAddMemory: (String, String) -> Unit,
    onUpdateMemory: (String, String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onOpenWriterPad: (WriterDocument?) -> Unit,
    onDeleteDocument: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary

    var newTaskTitle by rememberSaveable { mutableStateOf("") }
    var newTaskPriority by rememberSaveable { mutableStateOf(TaskPriority.HIGH) }

    var newMemCategory by rememberSaveable { mutableStateOf("Preference") }
    var newMemContent by rememberSaveable { mutableStateOf("") }
    var editingMemoryId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live Countdown Timers & Audio Alarms
        HudGlassCard(accentColor = accent) {
            HudSectionHeader(
                title = "Countdown Timers",
                badgeText = "${timers.size}",
                accentColor = if (timers.any { it.ringing }) CrimsonAlert else accent,
                trailing = {
                    if (timers.any { it.ringing }) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CrimsonAlert.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, CrimsonAlert),
                            modifier = Modifier.clickable(onClick = onSilenceAlarms)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SILENCE", style = MaterialTheme.typography.labelSmall, color = CrimsonAlert)
                            }
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("1m Focus" to 60, "5m Sprint" to 300, "15m Deep" to 900).forEach { (label, secs) ->
                    FilterChip(
                        selected = false,
                        onClick = { onStartQuickTimer(label, secs) },
                        leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text("+ $label", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (timers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    timers.forEach { timer ->
                        val timerAccent = when {
                            timer.ringing -> CrimsonAlert
                            timer.running -> EmeraldNominal
                            else -> AmberAlert
                        }
                        val progress = if (timer.totalSeconds > 0) {
                            timer.remainingSeconds.toFloat() / timer.totalSeconds.toFloat()
                        } else 0f

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = ObsidianElevated,
                            border = BorderStroke(1.dp, timerAccent.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(timer.label, style = MaterialTheme.typography.bodySmall, color = HudTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = if (timer.ringing) "00:00  •  ALARM" else formatSeconds(timer.remainingSeconds),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = timerAccent
                                        )
                                    }
                                    Row {
                                        if (!timer.ringing) {
                                            IconButton(onClick = { onToggleTimer(timer.id) }, modifier = Modifier.size(36.dp)) {
                                                Icon(
                                                    imageVector = if (timer.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Pause or resume timer",
                                                    tint = timerAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onDismissTimer(timer.id) }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Dismiss timer", tint = HudTextSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = timerAccent,
                                    trackColor = ObsidianVoid
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Priority Task Matrix
        HudGlassCard(accentColor = accent) {
            HudSectionHeader(title = "Priority Task Matrix", accentColor = accent)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("Add directive…", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = accent.copy(alpha = 0.3f),
                        focusedTextColor = HudTextPrimary,
                        unfocusedTextColor = HudTextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_task_input")
                )
                IconButton(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            onAddTask(newTaskTitle.trim(), newTaskPriority, 0)
                            newTaskTitle = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("add_task_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Priority Task", tint = accent)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskPriority.entries.forEach { p ->
                    FilterChip(
                        selected = newTaskPriority == p,
                        onClick = { newTaskPriority = p },
                        label = { Text(p.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            when (tasksState) {
                is UiState.Loading -> Text("Loading tasks…", style = MaterialTheme.typography.bodySmall, color = HudTextSecondary)
                is UiState.Error -> Text(tasksState.message, style = MaterialTheme.typography.bodySmall, color = CrimsonAlert)
                is UiState.Success -> {
                    val tasks = tasksState.data
                    if (tasks.isEmpty()) {
                        Text("No active directives. Ask JARVIS to create a task or timer.", style = MaterialTheme.typography.bodySmall, color = HudTextSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tasks.forEach { task ->
                                val pColor = when (TaskPriority.safeFrom(task.priority)) {
                                    TaskPriority.CRITICAL -> CrimsonAlert
                                    TaskPriority.HIGH -> AmberAlert
                                    TaskPriority.MEDIUM -> accent
                                    TaskPriority.LOW -> HudTextSecondary
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = ObsidianElevated.copy(alpha = 0.65f),
                                    border = BorderStroke(1.dp, pColor.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleTask(task) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Toggle task",
                                            tint = if (task.completed) EmeraldNominal else pColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (task.completed) HudTextSecondary else HudTextPrimary,
                                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                            Text(task.priority, style = MaterialTheme.typography.labelSmall, color = pColor)
                                        }
                                        IconButton(onClick = { onDeleteTask(task.id) }, modifier = Modifier.size(34.dp)) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete task", tint = HudTextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Persistent Long-Term Memory Vault
        HudGlassCard(accentColor = accent) {
            HudSectionHeader(title = "Memory Vault", badgeText = "CLOUD", accentColor = accent)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Preference", "Project", "Idea", "Personal").forEach { cat ->
                    FilterChip(
                        selected = newMemCategory == cat,
                        onClick = { newMemCategory = cat },
                        label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = newMemContent,
                    onValueChange = { newMemContent = it },
                    placeholder = { Text("Store persistent context…", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = accent.copy(alpha = 0.3f),
                        focusedTextColor = HudTextPrimary,
                        unfocusedTextColor = HudTextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("memory_input_field")
                )
                IconButton(
                    onClick = {
                        if (newMemContent.isNotBlank()) {
                            val editId = editingMemoryId
                            if (editId != null) {
                                onUpdateMemory(editId, newMemCategory, newMemContent)
                                editingMemoryId = null
                            } else {
                                onAddMemory(newMemCategory, newMemContent)
                            }
                            newMemContent = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("save_memory_button")
                ) {
                    Icon(
                        imageVector = if (editingMemoryId != null) Icons.Default.CheckCircle else Icons.Default.Add,
                        contentDescription = "Save memory note",
                        tint = accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            when (memoriesState) {
                is UiState.Loading -> Text("Decrypting memory vault…", style = MaterialTheme.typography.bodySmall, color = HudTextSecondary)
                is UiState.Error -> Text(memoriesState.message, style = MaterialTheme.typography.bodySmall, color = CrimsonAlert)
                is UiState.Success -> {
                    val memories = memoriesState.data
                    if (memories.isEmpty()) {
                        Text("Memory vault empty. Tell JARVIS something to remember across sessions.", style = MaterialTheme.typography.bodySmall, color = HudTextSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            memories.forEach { mem ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = ObsidianElevated.copy(alpha = 0.65f),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(mem.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
                                            Text(mem.content, style = MaterialTheme.typography.bodySmall, color = HudTextPrimary)
                                        }
                                        IconButton(
                                            onClick = {
                                                editingMemoryId = mem.id
                                                newMemCategory = mem.category
                                                newMemContent = mem.content
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit memory", tint = HudTextSecondary, modifier = Modifier.size(15.dp))
                                        }
                                        IconButton(onClick = { onDeleteMemory(mem.id) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete memory", tint = HudTextSecondary, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Expandable AI Studio Writer & Code Pad
        HudGlassCard(accentColor = accent) {
            HudSectionHeader(
                title = "AI Studio Writer Pad",
                accentColor = accent,
                trailing = {
                    Button(
                        onClick = { onOpenWriterPad(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.copy(alpha = 0.2f),
                            contentColor = accent
                        ),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("open_writer_pad_button")
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("OPEN PAD", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (documentsState) {
                is UiState.Loading -> Text("Syncing studio drafts…", style = MaterialTheme.typography.bodySmall, color = HudTextSecondary)
                is UiState.Error -> Text(documentsState.message, style = MaterialTheme.typography.bodySmall, color = CrimsonAlert)
                is UiState.Success -> {
                    val docs = documentsState.data
                    if (docs.isEmpty()) {
                        Text(
                            text = "Ask JARVIS to draft an email, report, script, or code snippet—or tap OPEN PAD.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HudTextSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            docs.forEach { doc ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = ObsidianElevated.copy(alpha = 0.72f),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.26f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenWriterPad(doc) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(doc.title, style = MaterialTheme.typography.bodySmall, color = HudTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${doc.docType} • ${doc.tone}", style = MaterialTheme.typography.labelSmall, color = accent)
                                        }
                                        IconButton(onClick = { onDeleteDocument(doc.id) }, modifier = Modifier.size(34.dp)) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete document", tint = HudTextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSeconds(total: Int): String {
    val mins = total / 60
    val secs = total % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
