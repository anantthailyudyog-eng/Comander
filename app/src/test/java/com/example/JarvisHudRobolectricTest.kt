package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.PreferencesRepository
import com.example.data.ActiveTimer
import com.example.data.ChatMessage
import com.example.data.CustomMacro
import com.example.data.HudMode
import com.example.data.MemoryNote
import com.example.data.MessageRole
import com.example.data.PriorityTask
import com.example.data.TaskPriority
import com.example.data.TelemetrySnapshot
import com.example.data.UiState
import com.example.data.VoiceSettings
import com.example.data.WriterDocType
import com.example.data.WriterDocument
import com.example.hardware.VoiceCaptureState
import com.example.ui.WriterEditorState
import com.example.ui.components.CenterNeuralCore
import com.example.ui.components.LeftCommandWing
import com.example.ui.components.RightVaultWing
import com.example.ui.components.TopTelemetryBar
import com.example.ui.screens.MacroBuilderOverlay
import com.example.ui.screens.WriterPadOverlay
import com.example.ui.theme.JarvisOsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JarvisHudRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `TopTelemetryBar switches HUD mode and triggers emergency stop`() {
        var activeMode by mutableStateOf(HudMode.STANDARD)
        var emergencyStopped = false

        composeRule.setContent {
            JarvisOsTheme(hudMode = activeMode) {
                TopTelemetryBar(
                    telemetry = TelemetrySnapshot(
                        clockTime = "10:15:00",
                        dateLabel = "Wed, Nov 25",
                        batteryPercent = 88,
                        networkLabel = "WIFI",
                        neuralLoadPercent = 35,
                        apiLatencyMs = 140L
                    ),
                    hudMode = activeMode,
                    onSelectMode = { activeMode = it },
                    onEmergencyStop = { emergencyStopped = true },
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("NEURAL 35%").assertExists()
        composeRule.onNodeWithText("140ms").assertExists()
        composeRule.onNodeWithText("MODE: CMD").assertExists()

        composeRule.onNodeWithTag("hud_mode_badge").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Coding/Dev Mode").performClick()
        composeRule.waitForIdle()

        assertEquals(HudMode.CODING, activeMode)
        composeRule.onNodeWithText("MODE: DEV").assertExists()

        composeRule.onNodeWithTag("emergency_stop_button").performClick()
        composeRule.waitForIdle()
        assertTrue(emergencyStopped)
    }

    @Test
    fun `CenterNeuralCore sends typed commands and triggers voice capture`() {
        val sentCommands = mutableListOf<String>()
        var arcClicked = false

        composeRule.setContent {
            JarvisOsTheme {
                CenterNeuralCore(
                    messages = listOf(
                        ChatMessage(
                            id = "1",
                            role = MessageRole.JARVIS,
                            text = "Mark-VII online.",
                            timestampLabel = "10:15:00"
                        )
                    ),
                    isProcessing = false,
                    voiceCaptureState = VoiceCaptureState.IDLE,
                    waveformBands = List(24) { 0.2f },
                    partialTranscript = "",
                    wakeListenerEnabled = false,
                    attachments = emptyList(),
                    liveVisionBitmap = null,
                    screenLinkActive = false,
                    continuousWatchEnabled = false,
                    onArcReactorClick = { arcClicked = true },
                    onToggleWakeListener = {},
                    onSendCommand = { sentCommands += it },
                    onPickImage = {},
                    onPickDocument = {},
                    onRemoveAttachment = {},
                    onAnalyzeLiveFrame = {},
                    onToggleContinuousWatch = {},
                    onStopLiveVision = {},
                    onCopyToast = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("send_command_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("command_input_field").performTextInput("Set 5 min focus timer")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send_command_button").assertIsEnabled()
        composeRule.onNodeWithTag("send_command_button").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Set 5 min focus timer"), sentCommands)

        composeRule.onNodeWithTag("mic_command_button").performClick()
        composeRule.waitForIdle()
        assertTrue(arcClicked)
    }

    @Test
    fun `LeftCommandWing triggers quick macros, custom macro builder, and locale switch`() {
        var briefingRan = false
        var macroBuilderOpened = false
        var updatedLocale: String? = null
        var ranCustomMacro: CustomMacro? = null

        val sampleMacro = CustomMacro(
            id = "m1",
            userId = "u1",
            name = "Standup Prep",
            voiceTrigger = "prepare standup",
            promptCommand = "Summarize pending critical tasks."
        )

        composeRule.setContent {
            JarvisOsTheme {
                LeftCommandWing(
                    hudMode = HudMode.STANDARD,
                    telemetry = TelemetrySnapshot(neuralLoadPercent = 22, ramUsagePercent = 40, batteryPercent = 92),
                    voiceSettings = VoiceSettings(),
                    screenLinkActive = false,
                    macrosState = UiState.Success(listOf(sampleMacro)),
                    onRunDailyBriefing = { briefingRan = true },
                    onQuickMacroDeepWork = {},
                    onQuickMacroCodeDebugger = {},
                    onQuickMacroScreenScan = {},
                    onToggleScreenLink = {},
                    onOpenCameraScanner = {},
                    onOpenMacroBuilder = { macroBuilderOpened = true },
                    onRunCustomMacro = { ranCustomMacro = it },
                    onDeleteCustomMacro = {},
                    onUpdateVoiceSettings = { _, _, _, locale -> updatedLocale = locale }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("macro_daily_briefing").performScrollTo().performClick()
        composeRule.onNodeWithTag("new_macro_button").performScrollTo().performClick()
        composeRule.onNodeWithText("Standup Prep").performScrollTo().performClick()
        composeRule.onNodeWithText("Hindi (HI-IN)").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(briefingRan)
        assertTrue(macroBuilderOpened)
        assertEquals("m1", ranCustomMacro?.id)
        assertEquals("hi-IN", updatedLocale)
    }

    @Test
    fun `RightVaultWing adds tasks, saves memories, starts quick timer, and opens writer pad`() {
        var timerStarted: Pair<String, Int>? = null
        var addedTask: Triple<String, TaskPriority, Int>? = null
        var addedMemory: Pair<String, String>? = null
        var writerOpened = false

        composeRule.setContent {
            JarvisOsTheme {
                RightVaultWing(
                    timers = listOf(ActiveTimer(id = "t1", label = "Compile Check", totalSeconds = 60, remainingSeconds = 45)),
                    tasksState = UiState.Success(listOf(PriorityTask(id = "k1", userId = "u1", title = "Audit rules", priority = "CRITICAL"))),
                    memoriesState = UiState.Success(listOf(MemoryNote(id = "mem1", userId = "u1", category = "Preference", content = "Uses Kotlin Compose"))),
                    documentsState = UiState.Success(listOf(WriterDocument(id = "d1", userId = "u1", title = "Deploy Note", docType = "REPORT", tone = "Executive"))),
                    onStartQuickTimer = { label, secs -> timerStarted = label to secs },
                    onToggleTimer = {},
                    onDismissTimer = {},
                    onSilenceAlarms = {},
                    onAddTask = { title, priority, secs -> addedTask = Triple(title, priority, secs) },
                    onToggleTask = {},
                    onDeleteTask = {},
                    onAddMemory = { cat, content -> addedMemory = cat to content },
                    onUpdateMemory = { _, _, _ -> },
                    onDeleteMemory = {},
                    onOpenWriterPad = { writerOpened = true },
                    onDeleteDocument = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("+ 1m Focus").performScrollTo().performClick()
        composeRule.onNodeWithTag("add_task_input").performScrollTo().performTextInput("Verify telemetry")
        composeRule.onNodeWithTag("add_task_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("memory_input_field").performScrollTo().performTextInput("Prefers Hinglish voice")
        composeRule.onNodeWithTag("save_memory_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("open_writer_pad_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("1m Focus" to 60, timerStarted)
        assertEquals(Triple("Verify telemetry", TaskPriority.HIGH, 0), addedTask)
        assertEquals("Preference" to "Prefers Hinglish voice", addedMemory)
        assertTrue(writerOpened)
    }

    @Test
    fun `MacroBuilderOverlay validates and creates custom macro`() {
        var createdMacro: Triple<String, String, String>? = null

        composeRule.setContent {
            JarvisOsTheme {
                MacroBuilderOverlay(
                    onCreateMacro = { name, trigger, prompt, _ ->
                        createdMacro = Triple(name, trigger, prompt)
                    },
                    onClose = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("save_macro_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("macro_name_input").performTextInput("Security Scan")
        composeRule.onNodeWithTag("macro_voice_input").performTextInput("scan code")
        composeRule.onNodeWithTag("macro_prompt_input").performTextInput("Audit open code for vulnerabilities.")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("save_macro_button").assertIsEnabled()
        composeRule.onNodeWithTag("save_macro_button").performClick()
        composeRule.waitForIdle()

        assertEquals(
            Triple("Security Scan", "scan code", "Audit open code for vulnerabilities."),
            createdMacro
        )
    }

    @Test
    fun `WriterPadOverlay updates and saves document`() {
        var savedWriter = false
        var latestContent = ""

        composeRule.setContent {
            JarvisOsTheme {
                WriterPadOverlay(
                    state = WriterEditorState(
                        open = true,
                        title = "Architecture Brief",
                        docType = WriterDocType.REPORT,
                        tone = "Executive",
                        content = "Initial draft"
                    ),
                    onUpdateDraft = { _, _, _, content ->
                        if (content != null) latestContent = content
                    },
                    onGenerateWithAi = { _, _, _, _ -> },
                    onSaveDocument = { savedWriter = true },
                    onCopyToast = {},
                    onClose = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("writer_content_editor").performScrollTo().performTextInput(" + verified")
        composeRule.onNodeWithTag("writer_save_button").performClick()
        composeRule.waitForIdle()

        assertTrue(latestContent.contains("verified"))
        assertTrue(savedWriter)
    }

    @Test
    fun `PreferencesRepository persists voice calibration settings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)
        repo.updateVoiceSettings {
            it.copy(
                ttsPitch = 1.15f,
                ttsRate = 0.9f,
                ttsMuted = true,
                speechLocaleTag = "hi-IN",
                wakeListenerEnabled = true
            )
        }
        val loaded = repo.voiceSettings.first()
        assertEquals(1.15f, loaded.ttsPitch, 0.001f)
        assertEquals(0.9f, loaded.ttsRate, 0.001f)
        assertTrue(loaded.ttsMuted)
        assertEquals("hi-IN", loaded.speechLocaleTag)
        assertTrue(loaded.wakeListenerEnabled)
    }
}
