package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.HudMode
import com.example.service.ScreenLinkController
import com.example.ui.ExternalCommandEvent
import com.example.ui.JarvisViewModel
import com.example.ui.components.CenterNeuralCore
import com.example.ui.components.HudBackgroundCanvas
import com.example.ui.components.HudToastOverlay
import com.example.ui.components.LeftCommandWing
import com.example.ui.components.RightVaultWing
import com.example.ui.components.TopTelemetryBar
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.ObsidianVoid

private enum class CompactHudTab {
    CORE,
    COMMAND,
    VAULT
}

@Composable
fun JarvisHudScreen(
    viewModel: JarvisViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hudMode by viewModel.hudMode.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val voiceCaptureState by viewModel.voiceCaptureState.collectAsStateWithLifecycle()
    val waveformBands by viewModel.waveformBands.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val voiceSettings by viewModel.voiceSettings.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val toasts by viewModel.toolToasts.collectAsStateWithLifecycle()
    val screenLinkActive by viewModel.screenLinkActive.collectAsStateWithLifecycle()
    val screenLinkFrame by viewModel.screenLinkFrame.collectAsStateWithLifecycle()
    val cameraSnapshot by viewModel.cameraSnapshot.collectAsStateWithLifecycle()
    val continuousWatchEnabled by viewModel.continuousWatchEnabled.collectAsStateWithLifecycle()
    val cameraScannerOpen by viewModel.cameraScannerOpen.collectAsStateWithLifecycle()
    val macroBuilderOpen by viewModel.macroBuilderOpen.collectAsStateWithLifecycle()
    val writerState by viewModel.writerState.collectAsStateWithLifecycle()

    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val macrosState by viewModel.macrosState.collectAsStateWithLifecycle()
    val documentsState by viewModel.documentsState.collectAsStateWithLifecycle()

    var compactTab by rememberSaveable { mutableStateOf(CompactHudTab.CORE) }
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Zero-permission Photo Picker for images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.attachFromUri(it) }
    }

    // SAF Document Picker for PDFs & Code files
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.attachFromUri(it) }
    }

    // Runtime Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAudioAction?.invoke()
        } else {
            viewModel.pushToast("Microphone Denied", "Enable audio permission to use Jarvis voice telemetry.", isAlert = true)
        }
        pendingAudioAction = null
    }

    // Runtime Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.openCameraScanner()
        } else {
            viewModel.pushToast("Camera Denied", "Enable camera permission for Optical Scan.", isAlert = true)
        }
    }

    // MediaProjection Screen Link Launcher
    val screenProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenLinkController.launchService(context, result.resultCode, result.data!!)
            viewModel.pushToast("Live Screen Link", "Optical screen feed active in PiP monitor.")
        } else {
            viewModel.pushToast("Screen Link", "Screen capture consent declined.", isAlert = true)
        }
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingAudioAction = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openCameraWithPermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.openCameraScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchScreenLinkConsent() {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    LaunchedEffect(Unit) {
        viewModel.externalEvents.collect { event ->
            when (event) {
                is ExternalCommandEvent.OpenUrl -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        viewModel.pushToast("Web Command", "Unable to open browser: ${e.localizedMessage}", isAlert = true)
                    }
                }
                ExternalCommandEvent.RequestScreenLink -> launchScreenLinkConsent()
                ExternalCommandEvent.OpenCameraScanner -> openCameraWithPermission()
            }
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val activeLiveFrame = screenLinkFrame ?: cameraSnapshot

    HudBackgroundCanvas(accentColor = accent, modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideHud = maxWidth >= 840.dp

            Scaffold(
                containerColor = ObsidianVoid.copy(alpha = 0f),
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!isWideHud) {
                        NavigationBar(
                            containerColor = ObsidianPanel.copy(alpha = 0.96f),
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            NavigationBarItem(
                                selected = compactTab == CompactHudTab.COMMAND,
                                onClick = { compactTab = CompactHudTab.COMMAND },
                                icon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_command), style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    indicatorColor = accent.copy(alpha = 0.18f),
                                    unselectedIconColor = HudTextSecondary,
                                    unselectedTextColor = HudTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_command_tab")
                            )
                            NavigationBarItem(
                                selected = compactTab == CompactHudTab.CORE,
                                onClick = { compactTab = CompactHudTab.CORE },
                                icon = { Icon(Icons.Default.Hub, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_core), style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    indicatorColor = accent.copy(alpha = 0.18f),
                                    unselectedIconColor = HudTextSecondary,
                                    unselectedTextColor = HudTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_core_tab")
                            )
                            NavigationBarItem(
                                selected = compactTab == CompactHudTab.VAULT,
                                onClick = { compactTab = CompactHudTab.VAULT },
                                icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_vault), style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    indicatorColor = accent.copy(alpha = 0.18f),
                                    unselectedIconColor = HudTextSecondary,
                                    unselectedTextColor = HudTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_vault_tab")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TopTelemetryBar(
                        telemetry = telemetry,
                        hudMode = hudMode,
                        onSelectMode = { viewModel.switchHudMode(it) },
                        onEmergencyStop = { viewModel.emergencyStop() },
                        onSignOut = onSignOut
                    )

                    val leftWingContent: @Composable (Modifier) -> Unit = { mod ->
                        LeftCommandWing(
                            hudMode = hudMode,
                            telemetry = telemetry,
                            voiceSettings = voiceSettings,
                            screenLinkActive = screenLinkActive,
                            macrosState = macrosState,
                            onRunDailyBriefing = {
                                compactTab = CompactHudTab.CORE
                                viewModel.runDailyBriefing()
                            },
                            onQuickMacroDeepWork = {
                                compactTab = CompactHudTab.CORE
                                viewModel.switchHudMode(HudMode.FOCUS)
                                viewModel.startCountdownTimer("Deep Work Protocol", 25 * 60)
                                viewModel.sendCommand("Engage Deep Work Focus/Stealth Mode. I am starting a 25-minute uninterrupted sprint.")
                            },
                            onQuickMacroCodeDebugger = {
                                compactTab = CompactHudTab.CORE
                                viewModel.switchHudMode(HudMode.CODING)
                                viewModel.sendCommand("Code Debugger engaged. Inspect any attached code or screen frame and await my stack trace.")
                            },
                            onQuickMacroScreenScan = {
                                compactTab = CompactHudTab.CORE
                                if (activeLiveFrame != null) {
                                    viewModel.analyzeCurrentLiveFrame()
                                } else {
                                    launchScreenLinkConsent()
                                }
                            },
                            onToggleScreenLink = {
                                if (screenLinkActive) viewModel.stopScreenLink() else launchScreenLinkConsent()
                            },
                            onOpenCameraScanner = { openCameraWithPermission() },
                            onOpenMacroBuilder = { viewModel.openMacroBuilder(true) },
                            onRunCustomMacro = { macro ->
                                compactTab = CompactHudTab.CORE
                                viewModel.runMacro(macro)
                            },
                            onDeleteCustomMacro = { viewModel.deleteMacro(it) },
                            onUpdateVoiceSettings = { pitch, rate, muted, locale ->
                                viewModel.updateVoiceCalibration(pitch, rate, muted, locale)
                            },
                            modifier = mod
                        )
                    }

                    val centerCoreContent: @Composable (Modifier) -> Unit = { mod ->
                        CenterNeuralCore(
                            messages = messages,
                            isProcessing = isProcessing,
                            voiceCaptureState = voiceCaptureState,
                            waveformBands = waveformBands,
                            partialTranscript = partialTranscript,
                            wakeListenerEnabled = voiceSettings.wakeListenerEnabled,
                            attachments = attachments,
                            liveVisionBitmap = activeLiveFrame,
                            screenLinkActive = screenLinkActive,
                            continuousWatchEnabled = continuousWatchEnabled,
                            onArcReactorClick = {
                                runWithMicPermission { viewModel.triggerArcReactorVoiceCapture() }
                            },
                            onToggleWakeListener = { enabled ->
                                if (enabled) {
                                    runWithMicPermission { viewModel.toggleWakeListener(true) }
                                } else {
                                    viewModel.toggleWakeListener(false)
                                }
                            },
                            onSendCommand = { prompt ->
                                viewModel.sendCommand(prompt, includeLiveVision = activeLiveFrame != null)
                            },
                            onPickImage = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickDocument = {
                                documentPickerLauncher.launch(
                                    arrayOf("application/pdf", "text/*", "application/json")
                                )
                            },
                            onRemoveAttachment = { viewModel.removeAttachment(it) },
                            onAnalyzeLiveFrame = { viewModel.analyzeCurrentLiveFrame() },
                            onToggleContinuousWatch = { viewModel.toggleContinuousWatch(it) },
                            onStopLiveVision = {
                                if (screenLinkActive) viewModel.stopScreenLink()
                                else ScreenLinkController.clearPreviewFrame()
                                viewModel.toggleContinuousWatch(false)
                            },
                            onCopyToast = {
                                viewModel.pushToast("Clipboard", "Copied to system clipboard.")
                            },
                            modifier = mod
                        )
                    }

                    val rightWingContent: @Composable (Modifier) -> Unit = { mod ->
                        RightVaultWing(
                            timers = timers,
                            tasksState = tasksState,
                            memoriesState = memoriesState,
                            documentsState = documentsState,
                            onStartQuickTimer = { label, secs -> viewModel.startCountdownTimer(label, secs) },
                            onToggleTimer = { viewModel.toggleTimerRunning(it) },
                            onDismissTimer = { viewModel.dismissTimer(it) },
                            onSilenceAlarms = { viewModel.stopAllAlarms() },
                            onAddTask = { title, priority, secs -> viewModel.addManualTask(title, priority, secs) },
                            onToggleTask = { viewModel.toggleTaskCompleted(it) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onAddMemory = { cat, content -> viewModel.addManualMemory(cat, content) },
                            onUpdateMemory = { id, cat, content -> viewModel.updateMemory(id, cat, content) },
                            onDeleteMemory = { viewModel.deleteMemory(it) },
                            onOpenWriterPad = { doc -> viewModel.openWriterPad(doc) },
                            onDeleteDocument = { viewModel.deleteDocument(it) },
                            modifier = mod
                        )
                    }

                    if (isWideHud) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            leftWingContent(Modifier.weight(0.28f))
                            centerCoreContent(Modifier.weight(0.44f))
                            rightWingContent(Modifier.weight(0.28f))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            when (compactTab) {
                                CompactHudTab.COMMAND -> leftWingContent(Modifier.fillMaxSize())
                                CompactHudTab.CORE -> centerCoreContent(Modifier.fillMaxSize())
                                CompactHudTab.VAULT -> rightWingContent(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }

            // Floating HUD Tool Toast Notifications
            HudToastOverlay(
                toasts = toasts,
                onDismiss = { viewModel.dismissToast(it) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 62.dp)
            )

            // Overlays
            if (cameraScannerOpen) {
                CameraScanOverlay(
                    continuousWatchEnabled = continuousWatchEnabled,
                    onToggleContinuousWatch = { viewModel.toggleContinuousWatch(it) },
                    onFrameCaptured = { bmp, analyze -> viewModel.onCameraFrameCaptured(bmp, analyze) },
                    onClose = { viewModel.closeCameraScanner() }
                )
            }

            if (macroBuilderOpen) {
                MacroBuilderOverlay(
                    onCreateMacro = { name, trigger, prompt, icon ->
                        viewModel.createCustomMacro(name, trigger, prompt, icon)
                    },
                    onClose = { viewModel.openMacroBuilder(false) }
                )
            }

            if (writerState.open) {
                WriterPadOverlay(
                    state = writerState,
                    onUpdateDraft = { title, type, tone, content ->
                        viewModel.updateWriterDraft(title, type, tone, content)
                    },
                    onGenerateWithAi = { title, type, tone, prompt ->
                        viewModel.generateDraftInWriter(title, type, tone, prompt)
                    },
                    onSaveDocument = { viewModel.saveCurrentWriterDocument() },
                    onCopyToast = { viewModel.pushToast("Clipboard", "Draft copied to clipboard.") },
                    onClose = { viewModel.closeWriterPad() }
                )
            }
        }
    }
}
