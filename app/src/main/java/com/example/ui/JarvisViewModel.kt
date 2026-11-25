package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.JarvisNeuralEngine
import com.example.core.PreferencesRepository
import com.example.core.SystemTelemetryMonitor
import com.example.data.ActiveTimer
import com.example.data.AttachmentItem
import com.example.data.ChatMessage
import com.example.data.CustomMacro
import com.example.data.HudMode
import com.example.data.JarvisRepository
import com.example.data.MemoryNote
import com.example.data.MessageRole
import com.example.data.PriorityTask
import com.example.data.TaskPriority
import com.example.data.TelemetrySnapshot
import com.example.data.ToolToast
import com.example.data.UiState
import com.example.data.VoiceSettings
import com.example.data.WriterDocType
import com.example.data.WriterDocument
import com.example.hardware.JarvisVoiceEngine
import com.example.hardware.VoiceCaptureState
import com.example.service.ScreenLinkController
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "JarvisViewModel"

sealed interface ExternalCommandEvent {
    data class OpenUrl(val url: String) : ExternalCommandEvent
    data object RequestScreenLink : ExternalCommandEvent
    data object OpenCameraScanner : ExternalCommandEvent
}

data class WriterEditorState(
    val open: Boolean = false,
    val docId: String? = null,
    val title: String = "Untitled Draft",
    val docType: WriterDocType = WriterDocType.NOTE,
    val tone: String = "Technical",
    val content: String = "",
    val generating: Boolean = false
)

class JarvisViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val repository: JarvisRepository,
    private val preferencesRepository: PreferencesRepository,
    private val telemetryMonitor: SystemTelemetryMonitor,
    private val neuralEngine: JarvisNeuralEngine
) : ViewModel() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val voiceEngine = JarvisVoiceEngine(
        context = appContext,
        onCommandRecognized = { spoken -> handleRecognizedVoiceCommand(spoken) },
        onWakeTriggered = {
            pushToast("Wake Link", "Voice signature confirmed. Awaiting directive, Sir.")
        },
        onVoiceError = { msg ->
            pushToast("Voice Telemetry", msg, isAlert = true)
        }
    )

    val telemetry: StateFlow<TelemetrySnapshot> = telemetryMonitor.telemetry
    val voiceCaptureState: StateFlow<VoiceCaptureState> = voiceEngine.captureState
    val waveformBands: StateFlow<List<Float>> = voiceEngine.waveformBands
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript

    val screenLinkActive: StateFlow<Boolean> = ScreenLinkController.isActive
    val screenLinkFrame: StateFlow<Bitmap?> = ScreenLinkController.latestFrame

    val voiceSettings: StateFlow<VoiceSettings> = preferencesRepository.voiceSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), VoiceSettings())

    val memoriesState: StateFlow<UiState<List<MemoryNote>>> = repository.observeMemories(currentUserId)
        .map<List<MemoryNote>, UiState<List<MemoryNote>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Memories stream error", root)
            emit(UiState.Error(root.message ?: "Failed to load memory vault"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val tasksState: StateFlow<UiState<List<PriorityTask>>> = repository.observeTasks(currentUserId)
        .map<List<PriorityTask>, UiState<List<PriorityTask>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Tasks stream error", root)
            emit(UiState.Error(root.message ?: "Failed to load priority tasks"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val macrosState: StateFlow<UiState<List<CustomMacro>>> = repository.observeMacros(currentUserId)
        .map<List<CustomMacro>, UiState<List<CustomMacro>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Macros stream error", root)
            emit(UiState.Error(root.message ?: "Failed to load custom macros"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val documentsState: StateFlow<UiState<List<WriterDocument>>> = repository.observeDocuments(currentUserId)
        .map<List<WriterDocument>, UiState<List<WriterDocument>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Documents stream error", root)
            emit(UiState.Error(root.message ?: "Failed to load writer documents"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _hudMode = MutableStateFlow(HudMode.STANDARD)
    val hudMode: StateFlow<HudMode> = _hudMode.asStateFlow()

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = "boot_msg",
                role = MessageRole.JARVIS,
                text = "J.A.R.V.I.S. Mark-VII online. Neural matrix calibrated, App Check attested, and Memory Vault synchronized. How may I assist you today, Sir?",
                timestampLabel = timeFormat.format(Date())
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _attachments = MutableStateFlow<List<AttachmentItem>>(emptyList())
    val attachments: StateFlow<List<AttachmentItem>> = _attachments.asStateFlow()

    private val _timers = MutableStateFlow<List<ActiveTimer>>(emptyList())
    val timers: StateFlow<List<ActiveTimer>> = _timers.asStateFlow()

    private val _toolToasts = MutableStateFlow<List<ToolToast>>(emptyList())
    val toolToasts: StateFlow<List<ToolToast>> = _toolToasts.asStateFlow()

    private val _continuousWatchEnabled = MutableStateFlow(false)
    val continuousWatchEnabled: StateFlow<Boolean> = _continuousWatchEnabled.asStateFlow()

    private val _cameraSnapshot = MutableStateFlow<Bitmap?>(null)
    val cameraSnapshot: StateFlow<Bitmap?> = _cameraSnapshot.asStateFlow()

    private val _cameraScannerOpen = MutableStateFlow(false)
    val cameraScannerOpen: StateFlow<Boolean> = _cameraScannerOpen.asStateFlow()

    private val _macroBuilderOpen = MutableStateFlow(false)
    val macroBuilderOpen: StateFlow<Boolean> = _macroBuilderOpen.asStateFlow()

    private val _writerState = MutableStateFlow(WriterEditorState())
    val writerState: StateFlow<WriterEditorState> = _writerState.asStateFlow()

    private val _externalEvents = MutableSharedFlow<ExternalCommandEvent>(extraBufferCapacity = 8)
    val externalEvents: SharedFlow<ExternalCommandEvent> = _externalEvents.asSharedFlow()

    private var activeAiJob: Job? = null
    private var activeWriterJob: Job? = null
    private var continuousWatchJob: Job? = null

    init {
        viewModelScope.launch {
            voiceSettings.collect { settings ->
                voiceEngine.updateSettings(
                    pitch = settings.ttsPitch,
                    rate = settings.ttsRate,
                    muted = settings.ttsMuted,
                    localeTag = settings.speechLocaleTag
                )
            }
        }
        startTimerTicker()
    }

    private fun startTimerTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val currentList = _timers.value
                if (currentList.isEmpty()) continue
                var alarmTriggeredLabel: String? = null
                val updated = currentList.map { timer ->
                    if (!timer.running || timer.ringing) {
                        timer
                    } else if (timer.remainingSeconds <= 1) {
                        alarmTriggeredLabel = timer.label
                        timer.copy(remainingSeconds = 0, running = false, ringing = true)
                    } else {
                        timer.copy(remainingSeconds = timer.remainingSeconds - 1)
                    }
                }
                _timers.value = updated
                alarmTriggeredLabel?.let { label ->
                    voiceEngine.playTimerAlarm()
                    pushToast("Timer Alert", "Countdown complete: $label", isAlert = true)
                    appendMessage(
                        role = MessageRole.TOOL,
                        text = "TIMER COMPLETE: \"$label\" has reached 00:00."
                    )
                    voiceEngine.speak("Sir, your timer for $label has concluded.")
                }
            }
        }
    }

    fun sendCommand(rawPrompt: String, includeLiveVision: Boolean = false) {
        val prompt = rawPrompt.trim()
        val attached = _attachments.value
        if (prompt.isEmpty() && attached.isEmpty()) return
        val effectivePrompt = if (prompt.isEmpty()) {
            "Analyze the attached files or visual input and summarize key insights."
        } else {
            prompt
        }

        val attachmentNames = attached.map { it.name }
        appendMessage(
            role = MessageRole.USER,
            text = effectivePrompt,
            attachmentNames = attachmentNames
        )
        _attachments.value = emptyList()

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isProcessing.value = true
            telemetryMonitor.updateNeuralMetrics(loadPercent = 74)
            val visionFrame = if (includeLiveVision) {
                screenLinkFrame.value ?: _cameraSnapshot.value
            } else {
                null
            }

            try {
                val memories = (memoriesState.value as? UiState.Success)?.data ?: emptyList()
                val tasks = (tasksState.value as? UiState.Success)?.data ?: emptyList()
                val result = neuralEngine.sendCommand(
                    prompt = effectivePrompt,
                    attachments = attached,
                    liveVisionBitmap = visionFrame,
                    hudMode = _hudMode.value,
                    memories = memories,
                    tasks = tasks,
                    activeTimersCount = _timers.value.count { it.running },
                    onToolCall = { call -> executeAutonomousTool(call) }
                )

                telemetryMonitor.updateNeuralMetrics(loadPercent = 24, latencyMs = result.latencyMs)
                appendMessage(role = MessageRole.JARVIS, text = result.text)
                voiceEngine.speak(result.text)
            } catch (e: TimeoutCancellationException) {
                telemetryMonitor.updateNeuralMetrics(loadPercent = 18)
                appendMessage(
                    role = MessageRole.ERROR,
                    text = "Neural link timed out after 60 seconds. Please retry your directive."
                )
            } catch (e: CancellationException) {
                telemetryMonitor.updateNeuralMetrics(loadPercent = 14)
                throw e
            } catch (e: Exception) {
                telemetryMonitor.updateNeuralMetrics(loadPercent = 18)
                val msg = e.localizedMessage ?: "Neural engine request failed."
                appendMessage(role = MessageRole.ERROR, text = msg)
                pushToast("Neural Error", msg, isAlert = true)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun executeAutonomousTool(call: FunctionCallPart): JsonObject {
        val args = call.args
        return when (call.name) {
            "create_task_or_reminder" -> {
                val title = args["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                    .ifBlank { "Scheduled Directive" }
                val seconds = args["time_seconds"]?.jsonPrimitive?.intOrNull?.coerceIn(0, 86400) ?: 0
                val priority = TaskPriority.safeFrom(args["priority"]?.jsonPrimitive?.content.orEmpty())

                val result = repository.addTask(currentUserId, title, priority, seconds.toLong())
                if (seconds > 0) {
                    startCountdownTimer(title, seconds)
                }
                val ok = result.isSuccess
                val detail = if (seconds > 0) {
                    "Task logged (${priority.name}) + ${formatDuration(seconds)} timer started."
                } else {
                    "Task logged to Priority Matrix (${priority.name})."
                }
                pushToast("Task & Timer", "$title — $detail", isAlert = !ok)
                appendMessage(MessageRole.TOOL, "TOOL [create_task_or_reminder]: $title | $detail")
                buildJsonObject {
                    put("status", if (ok) "success" else "error")
                    put("title", title)
                    put("priority", priority.name)
                    put("timer_seconds", seconds)
                }
            }

            "save_memory_note" -> {
                val category = args["category"]?.jsonPrimitive?.content?.trim().orEmpty()
                    .ifBlank { "Preference" }
                val content = args["content"]?.jsonPrimitive?.content?.trim().orEmpty()
                val result = repository.addMemory(currentUserId, category, content)
                val ok = result.isSuccess
                pushToast("Memory Vault", "[$category] $content", isAlert = !ok)
                appendMessage(MessageRole.TOOL, "TOOL [save_memory_note]: Stored under [$category]")
                buildJsonObject {
                    put("status", if (ok) "success" else "error")
                    put("category", category)
                    put("saved", ok)
                }
            }

            "execute_web_command" -> {
                val action = args["action"]?.jsonPrimitive?.content?.trim().orEmpty()
                val query = args["query"]?.jsonPrimitive?.content?.trim().orEmpty()
                val outcome = dispatchWebOrWorkspaceAction(action, query)
                pushToast("Web / System Link", "$action -> $query")
                appendMessage(MessageRole.TOOL, "TOOL [execute_web_command]: $outcome")
                buildJsonObject {
                    put("status", "executed")
                    put("action", action)
                    put("detail", outcome)
                }
            }

            "generate_document" -> {
                val docType = WriterDocType.safeFrom(args["type"]?.jsonPrimitive?.content.orEmpty())
                val tone = args["tone"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "Technical" }
                val prompt = args["prompt"]?.jsonPrimitive?.content?.trim().orEmpty()
                val title = prompt.lineSequence().firstOrNull()?.take(48)?.ifBlank { "JARVIS ${docType.label}" }
                    ?: "JARVIS ${docType.label}"
                pushToast("AI Studio Writer", "Drafting ${docType.label} ($tone)...")
                appendMessage(MessageRole.TOOL, "TOOL [generate_document]: Opening Writer Pad for $title")
                generateDraftInWriter(title = title, docType = docType, tone = tone, prompt = prompt)
                buildJsonObject {
                    put("status", "opened_writer")
                    put("type", docType.name)
                    put("tone", tone)
                    put("title", title)
                }
            }

            "switch_hud_mode" -> {
                val modeRaw = args["mode"]?.jsonPrimitive?.content.orEmpty()
                val mode = HudMode.fromNameOrLabel(modeRaw)
                _hudMode.value = mode
                pushToast("HUD Mode Shift", "Active matrix: ${mode.displayName}")
                appendMessage(MessageRole.TOOL, "TOOL [switch_hud_mode]: Engaged ${mode.displayName}")
                buildJsonObject {
                    put("status", "switched")
                    put("active_mode", mode.displayName)
                }
            }

            else -> buildJsonObject {
                put("status", "unknown_tool")
                put("name", JsonPrimitive(call.name))
            }
        }
    }

    private fun dispatchWebOrWorkspaceAction(action: String, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return when (action.lowercase()) {
            "open_url" -> {
                val normalized = when {
                    query.startsWith("https://", ignoreCase = true) ||
                        query.startsWith("http://", ignoreCase = true) -> query
                    else -> "https://$query"
                }
                _externalEvents.tryEmit(ExternalCommandEvent.OpenUrl(normalized))
                "Opened URL $normalized"
            }

            "google_search" -> {
                val url = "https://www.google.com/search?q=$encoded"
                _externalEvents.tryEmit(ExternalCommandEvent.OpenUrl(url))
                "Launched Google search for \"$query\""
            }

            "youtube_search" -> {
                val url = "https://www.youtube.com/results?search_query=$encoded"
                _externalEvents.tryEmit(ExternalCommandEvent.OpenUrl(url))
                "Launched YouTube search for \"$query\""
            }

            "maps_search" -> {
                val url = "https://www.google.com/maps/search/?api=1&query=$encoded"
                _externalEvents.tryEmit(ExternalCommandEvent.OpenUrl(url))
                "Launched Maps lookup for \"$query\""
            }

            "workspace_tool" -> {
                when (query.lowercase()) {
                    "writer", "pad" -> openWriterPad()
                    "camera", "optical", "scan" -> _externalEvents.tryEmit(ExternalCommandEvent.OpenCameraScanner)
                    "screen_link", "screen" -> _externalEvents.tryEmit(ExternalCommandEvent.RequestScreenLink)
                    "macros", "macro" -> _macroBuilderOpen.value = true
                    "diagnostics", "diag" -> switchHudMode(HudMode.DIAGNOSTIC)
                    else -> openWriterPad()
                }
                "Activated HUD workspace tool: $query"
            }

            else -> "Executed workspace directive: $query"
        }
    }

    fun runDailyBriefing() {
        val memories = (memoriesState.value as? UiState.Success)?.data ?: emptyList()
        val tasks = (tasksState.value as? UiState.Success)?.data ?: emptyList()
        val t = telemetry.value
        val pendingCount = tasks.count { !it.completed }

        val briefingPrompt = """
            Execute my Daily Briefing right now.
            Compile:
            1. Current time (${t.clockTime}, ${t.dateLabel}) and device telemetry (Battery ${t.batteryPercent}%, Network ${t.networkLabel}).
            2. Summary of my $pendingCount pending tasks from the Priority Task Matrix and relevant highlights from my ${memories.size} Memory Vault notes.
            3. A sharp, prioritized tactical productivity plan for today in J.A.R.V.I.S.'s signature loyal, high-tech persona.
        """.trimIndent()
        sendCommand(briefingPrompt)
    }

    fun switchHudMode(mode: HudMode) {
        _hudMode.value = mode
        pushToast("HUD Mode", "Switched to ${mode.displayName}")
    }

    fun startCountdownTimer(label: String, seconds: Int) {
        if (seconds <= 0) return
        val timer = ActiveTimer(
            id = UUID.randomUUID().toString(),
            label = label.take(60),
            totalSeconds = seconds,
            remainingSeconds = seconds,
            running = true,
            ringing = false
        )
        _timers.update { listOf(timer) + it }
    }

    fun toggleTimerRunning(timerId: String) {
        _timers.update { list ->
            list.map {
                if (it.id == timerId && !it.ringing) it.copy(running = !it.running) else it
            }
        }
    }

    fun dismissTimer(timerId: String) {
        voiceEngine.stopAlarm()
        _timers.update { list -> list.filterNot { it.id == timerId } }
    }

    fun stopAllAlarms() {
        voiceEngine.stopAlarm()
        _timers.update { list -> list.map { it.copy(ringing = false) } }
    }

    // --- Voice & Wake Controls ---

    fun triggerArcReactorVoiceCapture() {
        when (voiceCaptureState.value) {
            VoiceCaptureState.COMMAND_LISTENING -> voiceEngine.stopAllVoiceActivity()
            VoiceCaptureState.SPEAKING -> voiceEngine.stopSpeaking()
            else -> voiceEngine.startCommandCapture()
        }
    }

    fun toggleWakeListener(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateVoiceSettings { it.copy(wakeListenerEnabled = enabled) }
        }
        voiceEngine.setWakeListenerEnabled(enabled)
        pushToast(
            "Wake Listener",
            if (enabled) "Continuous \"Jarvis\" wake listener armed." else "Wake listener disarmed."
        )
    }

    fun updateVoiceCalibration(pitch: Float? = null, rate: Float? = null, muted: Boolean? = null, localeTag: String? = null) {
        viewModelScope.launch {
            preferencesRepository.updateVoiceSettings { current ->
                current.copy(
                    ttsPitch = pitch ?: current.ttsPitch,
                    ttsRate = rate ?: current.ttsRate,
                    ttsMuted = muted ?: current.ttsMuted,
                    speechLocaleTag = localeTag ?: current.speechLocaleTag
                )
            }
        }
    }

    private fun handleRecognizedVoiceCommand(spoken: String) {
        val macros = (macrosState.value as? UiState.Success)?.data ?: emptyList()
        val matchedMacro = macros.firstOrNull { macro ->
            macro.voiceTrigger.isNotBlank() &&
                spoken.contains(macro.voiceTrigger.trim(), ignoreCase = true)
        }
        if (matchedMacro != null) {
            pushToast("Voice Macro", "Triggered \"${matchedMacro.name}\"")
            sendCommand(matchedMacro.promptCommand)
        } else {
            sendCommand(spoken, includeLiveVision = screenLinkActive.value || _cameraSnapshot.value != null)
        }
    }

    // --- Multimodal Vision & Attachments ---

    fun openCameraScanner() {
        _cameraScannerOpen.value = true
    }

    fun closeCameraScanner() {
        _cameraScannerOpen.value = false
    }

    fun onCameraFrameCaptured(bitmap: Bitmap, analyzeNow: Boolean = true) {
        val scaled = scaleBitmap(bitmap, 1280)
        _cameraSnapshot.value = scaled
        if (analyzeNow) {
            _cameraScannerOpen.value = false
            appendMessage(
                role = MessageRole.USER,
                text = "Optical Scan: Inspect this camera capture—identify objects, read handwritten notes or code, and summarize.",
                attachmentNames = listOf("Camera Optical Capture")
            )
            analyzeBitmapDirect(scaled, "Identify physical objects, read any handwritten notes/text, and provide tactical observations.")
        }
    }

    fun analyzeCurrentLiveFrame() {
        val frame = screenLinkFrame.value ?: _cameraSnapshot.value
        if (frame == null) {
            pushToast("Visual Link", "No active screen or camera frame available.", isAlert = true)
            return
        }
        val sourceLabel = if (screenLinkFrame.value != null) "Screen Link Frame" else "Camera Frame"
        appendMessage(
            role = MessageRole.USER,
            text = "Analyze current $sourceLabel for errors, insights, or step-by-step guidance.",
            attachmentNames = listOf(sourceLabel)
        )
        analyzeBitmapDirect(frame, "Read everything visible on screen/camera: diagnose errors, summarize content, or guide next steps.")
    }

    private fun analyzeBitmapDirect(bitmap: Bitmap, prompt: String) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isProcessing.value = true
            telemetryMonitor.updateNeuralMetrics(loadPercent = 82)
            val startMs = System.currentTimeMillis()
            try {
                val analysis = neuralEngine.analyzeVisualFrame(bitmap, prompt)
                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                telemetryMonitor.updateNeuralMetrics(loadPercent = 24, latencyMs = latency)
                appendMessage(MessageRole.JARVIS, analysis)
                voiceEngine.speak(analysis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Optical analysis failed."
                appendMessage(MessageRole.ERROR, msg)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun toggleContinuousWatch(enabled: Boolean) {
        _continuousWatchEnabled.value = enabled
        continuousWatchJob?.cancel()
        if (!enabled) {
            pushToast("Continuous Watch", "Autonomous optical watch paused.")
            return
        }
        pushToast("Continuous Watch", "Monitoring visual telemetry every 7 seconds.")
        continuousWatchJob = viewModelScope.launch {
            while (isActive && _continuousWatchEnabled.value) {
                val frame = screenLinkFrame.value ?: _cameraSnapshot.value
                if (frame != null && !_isProcessing.value) {
                    try {
                        val note = neuralEngine.analyzeVisualFrame(
                            frame,
                            "Continuous Watch check: briefly note any error, change, or key detail visible in 1-2 crisp sentences."
                        )
                        appendMessage(MessageRole.TOOL, "OPTICAL WATCH: $note")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Continuous watch cycle error: ${e.message}")
                    }
                }
                delay(7000L)
            }
        }
    }

    fun attachFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = appContext.contentResolver
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                var fileName = "attachment"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) fileName = cursor.getString(idx) ?: fileName
                    }
                }

                val item = when {
                    mime.startsWith("image/") -> {
                        val rawBitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        val scaled = rawBitmap?.let { scaleBitmap(it, 1280) }
                        AttachmentItem(
                            id = UUID.randomUUID().toString(),
                            name = fileName,
                            mimeType = mime,
                            bitmap = scaled
                        )
                    }
                    mime == "application/pdf" -> {
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null && bytes.size > 15 * 1024 * 1024) {
                            withContext(Dispatchers.Main) {
                                pushToast("Attachment Limit", "PDF exceeds 15MB inline limit.", isAlert = true)
                            }
                            return@launch
                        }
                        AttachmentItem(
                            id = UUID.randomUUID().toString(),
                            name = fileName,
                            mimeType = mime,
                            rawBytes = bytes
                        )
                    }
                    else -> {
                        val textContent = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                            reader.readText().take(24_000)
                        }.orEmpty()
                        AttachmentItem(
                            id = UUID.randomUUID().toString(),
                            name = fileName,
                            mimeType = mime,
                            textPreview = textContent
                        )
                    }
                }
                _attachments.update { it + item }
                withContext(Dispatchers.Main) {
                    pushToast("Payload Attached", "$fileName ready for neural analysis.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach URI", e)
                withContext(Dispatchers.Main) {
                    pushToast("Attachment Error", e.localizedMessage ?: "Unable to read file", isAlert = true)
                }
            }
        }
    }

    fun removeAttachment(id: String) {
        _attachments.update { list -> list.filterNot { it.id == id } }
    }

    // --- Memory Vault, Tasks, Macros, Writer CRUD ---

    fun addManualMemory(category: String, content: String) {
        viewModelScope.launch {
            val res = repository.addMemory(currentUserId, category, content)
            if (res.isSuccess) pushToast("Memory Vault", "Memory archived.")
            else pushToast("Memory Error", res.exceptionOrNull()?.localizedMessage ?: "Failed", isAlert = true)
        }
    }

    fun updateMemory(memoryId: String, category: String, content: String) {
        viewModelScope.launch {
            val res = repository.updateMemory(currentUserId, memoryId, category, content)
            if (res.isSuccess) pushToast("Memory Vault", "Memory updated.")
            else pushToast("Memory Error", res.exceptionOrNull()?.localizedMessage ?: "Failed", isAlert = true)
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(currentUserId, memoryId)
        }
    }

    fun addManualTask(title: String, priority: TaskPriority, durationSeconds: Int) {
        viewModelScope.launch {
            val res = repository.addTask(currentUserId, title, priority, durationSeconds.toLong())
            if (res.isSuccess && durationSeconds > 0) {
                startCountdownTimer(title, durationSeconds)
            }
            if (res.isFailure) {
                pushToast("Task Error", res.exceptionOrNull()?.localizedMessage ?: "Failed", isAlert = true)
            }
        }
    }

    fun toggleTaskCompleted(task: PriorityTask) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(currentUserId, task.id, !task.completed)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, taskId)
        }
    }

    fun openMacroBuilder(open: Boolean) {
        _macroBuilderOpen.value = open
    }

    fun createCustomMacro(name: String, voiceTrigger: String, promptCommand: String, iconKey: String) {
        viewModelScope.launch {
            val res = repository.addMacro(currentUserId, name, voiceTrigger, promptCommand, iconKey)
            if (res.isSuccess) {
                _macroBuilderOpen.value = false
                pushToast("Macro Forged", "\"$name\" added to Command Hub.")
            } else {
                pushToast("Macro Error", res.exceptionOrNull()?.localizedMessage ?: "Failed", isAlert = true)
            }
        }
    }

    fun runMacro(macro: CustomMacro) {
        pushToast("Macro Engaged", macro.name)
        sendCommand(macro.promptCommand, includeLiveVision = screenLinkActive.value)
    }

    fun deleteMacro(macroId: String) {
        viewModelScope.launch {
            repository.deleteMacro(currentUserId, macroId)
        }
    }

    // --- AI Studio Writer & Code Pad ---

    fun openWriterPad(doc: WriterDocument? = null) {
        if (doc == null) {
            _writerState.value = WriterEditorState(open = true)
        } else {
            _writerState.value = WriterEditorState(
                open = true,
                docId = doc.id,
                title = doc.title,
                docType = WriterDocType.safeFrom(doc.docType),
                tone = doc.tone,
                content = doc.content
            )
        }
    }

    fun closeWriterPad() {
        activeWriterJob?.cancel()
        _writerState.update { it.copy(open = false, generating = false) }
    }

    fun updateWriterDraft(
        title: String? = null,
        docType: WriterDocType? = null,
        tone: String? = null,
        content: String? = null
    ) {
        _writerState.update { state ->
            state.copy(
                title = title ?: state.title,
                docType = docType ?: state.docType,
                tone = tone ?: state.tone,
                content = content ?: state.content
            )
        }
    }

    fun generateDraftInWriter(title: String, docType: WriterDocType, tone: String, prompt: String) {
        activeWriterJob?.cancel()
        _writerState.value = WriterEditorState(
            open = true,
            docId = null,
            title = title,
            docType = docType,
            tone = tone,
            content = "",
            generating = true
        )
        activeWriterJob = viewModelScope.launch {
            try {
                neuralEngine.streamWriterDocument(docType, tone, prompt).collect { accumulated ->
                    _writerState.update { it.copy(content = accumulated) }
                }
                val finalState = _writerState.value
                _writerState.update { it.copy(generating = false) }
                val saveRes = repository.saveDocument(
                    userId = currentUserId,
                    title = finalState.title,
                    docType = finalState.docType,
                    tone = finalState.tone,
                    content = finalState.content
                )
                if (saveRes.isSuccess) {
                    _writerState.update { it.copy(docId = saveRes.getOrNull()) }
                    pushToast("Writer Pad", "Draft saved to Vault.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _writerState.update { it.copy(generating = false) }
                pushToast("Writer Error", e.localizedMessage ?: "Draft generation failed.", isAlert = true)
            }
        }
    }

    fun saveCurrentWriterDocument() {
        val state = _writerState.value
        viewModelScope.launch {
            val res = if (state.docId.isNullOrBlank()) {
                repository.saveDocument(currentUserId, state.title, state.docType, state.tone, state.content)
                    .map { newId -> _writerState.update { it.copy(docId = newId) } }
            } else {
                repository.updateDocument(currentUserId, state.docId, state.title, state.docType, state.tone, state.content)
            }
            if (res.isSuccess) pushToast("Writer Pad", "Document synchronized.")
            else pushToast("Writer Error", res.exceptionOrNull()?.localizedMessage ?: "Save failed", isAlert = true)
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            repository.deleteDocument(currentUserId, docId)
        }
    }

    fun stopScreenLink() {
        ScreenLinkController.stopProjection(appContext)
        _continuousWatchEnabled.value = false
        continuousWatchJob?.cancel()
        pushToast("Screen Link", "Visual screen feed terminated.")
    }

    fun requestScreenLink() {
        _externalEvents.tryEmit(ExternalCommandEvent.RequestScreenLink)
    }

    fun emergencyStop() {
        activeAiJob?.cancel()
        activeWriterJob?.cancel()
        continuousWatchJob?.cancel()
        _continuousWatchEnabled.value = false
        _isProcessing.value = false
        voiceEngine.stopAllVoiceActivity()
        stopAllAlarms()
        ScreenLinkController.stopProjection(appContext)
        neuralEngine.resetSession()
        telemetryMonitor.updateNeuralMetrics(loadPercent = 10)
        viewModelScope.launch {
            preferencesRepository.updateVoiceSettings { it.copy(wakeListenerEnabled = false) }
        }
        appendMessage(MessageRole.TOOL, "EMERGENCY RESET: All neural streams, voice listeners, alarms, and vision links halted.")
        pushToast("Emergency Reset", "All autonomous subsystems safely halted.", isAlert = true)
    }

    fun pushToast(toolName: String, summary: String, isAlert: Boolean = false) {
        val toast = ToolToast(
            id = UUID.randomUUID().toString(),
            toolName = toolName,
            summary = summary.take(120),
            isAlert = isAlert
        )
        _toolToasts.update { (it + toast).takeLast(3) }
        viewModelScope.launch {
            delay(4200L)
            _toolToasts.update { list -> list.filterNot { it.id == toast.id } }
        }
    }

    fun dismissToast(id: String) {
        _toolToasts.update { list -> list.filterNot { it.id == id } }
    }

    private fun appendMessage(
        role: MessageRole,
        text: String,
        attachmentNames: List<String> = emptyList()
    ) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            text = text,
            timestampLabel = timeFormat.format(Date()),
            attachmentNames = attachmentNames
        )
        _messages.update { it + msg }
    }

    private fun scaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }

    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
        telemetryMonitor.release()
        ScreenLinkController.stopProjection(appContext)
    }
}
