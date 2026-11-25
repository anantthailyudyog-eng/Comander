package com.example.hardware

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

private const val TAG = "JarvisVoiceEngine"
private const val BAND_COUNT = 24

enum class VoiceCaptureState {
    IDLE,
    WAKE_LISTENING,
    COMMAND_LISTENING,
    SPEAKING
}

class JarvisVoiceEngine(
    context: Context,
    private val onCommandRecognized: (String) -> Unit,
    private val onWakeTriggered: () -> Unit,
    private val onVoiceError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private var currentLocaleTag: String = "en-IN"
    private var currentPitch: Float = 0.94f
    private var currentRate: Float = 1.04f
    private var isMuted: Boolean = false
    private var wakeModeActive: Boolean = false

    private val _captureState = MutableStateFlow(VoiceCaptureState.IDLE)
    val captureState: StateFlow<VoiceCaptureState> = _captureState.asStateFlow()

    private val _waveformBands = MutableStateFlow(List(BAND_COUNT) { 0.08f })
    val waveformBands: StateFlow<List<Float>> = _waveformBands.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private var ttsWaveJob: Job? = null
    private var restartWakeJob: Job? = null
    private var alarmJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            applyTtsSettings(currentPitch, currentRate, currentLocaleTag)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        _captureState.value = VoiceCaptureState.SPEAKING
                        startSpeechWaveAnimation()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        stopSpeechWaveAnimation()
                        if (_captureState.value == VoiceCaptureState.SPEAKING) {
                            _captureState.value = VoiceCaptureState.IDLE
                        }
                        if (wakeModeActive) {
                            scheduleWakeRestart(350L)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        stopSpeechWaveAnimation()
                        if (_captureState.value == VoiceCaptureState.SPEAKING) {
                            _captureState.value = VoiceCaptureState.IDLE
                        }
                    }
                }
            })
        } else {
            Log.w(TAG, "TTS initialization failed with status=$status")
        }
    }

    fun updateSettings(pitch: Float, rate: Float, muted: Boolean, localeTag: String) {
        currentPitch = pitch.coerceIn(0.5f, 1.8f)
        currentRate = rate.coerceIn(0.6f, 2.0f)
        isMuted = muted
        currentLocaleTag = localeTag
        if (muted) {
            stopSpeaking()
        }
        applyTtsSettings(currentPitch, currentRate, localeTag)
    }

    private fun applyTtsSettings(pitch: Float, rate: Float, localeTag: String) {
        if (!ttsReady) return
        val engine = tts ?: return
        engine.setPitch(pitch)
        engine.setSpeechRate(rate)
        val locale = Locale.forLanguageTag(localeTag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
    }

    fun speak(rawText: String) {
        if (isMuted || !ttsReady || rawText.isBlank()) return
        val cleanText = rawText
            .replace(Regex("```[\\s\\S]*?```"), " Code block output is displayed in the terminal. ")
            .replace(Regex("[#*_`>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(900)

        if (cleanText.isEmpty()) return

        mainHandler.post {
            stopListeningInternal()
            val params = Bundle()
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "jarvis_utt_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            tts?.stop()
            stopSpeechWaveAnimation()
            if (_captureState.value == VoiceCaptureState.SPEAKING) {
                _captureState.value = VoiceCaptureState.IDLE
            }
        }
    }

    fun setWakeListenerEnabled(enabled: Boolean) {
        wakeModeActive = enabled
        if (enabled) {
            if (_captureState.value == VoiceCaptureState.IDLE) {
                startListening(isWakeMode = true)
            }
        } else {
            restartWakeJob?.cancel()
            if (_captureState.value == VoiceCaptureState.WAKE_LISTENING) {
                stopListeningInternal()
                _captureState.value = VoiceCaptureState.IDLE
                resetWaveform()
            }
        }
    }

    fun startCommandCapture() {
        mainHandler.post {
            stopSpeaking()
            restartWakeJob?.cancel()
            startListening(isWakeMode = false)
        }
    }

    fun stopAllVoiceActivity() {
        wakeModeActive = false
        restartWakeJob?.cancel()
        stopSpeaking()
        stopListeningInternal()
        _captureState.value = VoiceCaptureState.IDLE
        _partialTranscript.value = ""
        resetWaveform()
    }

    private fun startListening(isWakeMode: Boolean) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onVoiceError("Speech recognition service unavailable on this device.")
            wakeModeActive = false
            _captureState.value = VoiceCaptureState.IDLE
            return
        }

        stopListeningInternal()
        _partialTranscript.value = ""
        _captureState.value = if (isWakeMode) VoiceCaptureState.WAKE_LISTENING else VoiceCaptureState.COMMAND_LISTENING

        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                updateMicWaveform(normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                resetWaveform()
                val shouldRetryWake = wakeModeActive &&
                    (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_CLIENT)

                if (isWakeMode && shouldRetryWake) {
                    _captureState.value = VoiceCaptureState.IDLE
                    scheduleWakeRestart(450L)
                } else {
                    _captureState.value = VoiceCaptureState.IDLE
                    if (!isWakeMode && error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        onVoiceError("Voice telemetry error code: $error")
                    } else if (wakeModeActive) {
                        scheduleWakeRestart(900L)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                resetWaveform()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim().orEmpty()
                _partialTranscript.value = ""

                if (transcript.isNotBlank()) {
                    if (isWakeMode) {
                        val lower = transcript.lowercase()
                        val wakeHit = lower.contains("jarvis") || lower.contains("जार्विस")
                        if (wakeHit) {
                            val stripped = transcript
                                .replace(Regex("(?i)hey\\s+jarvis|ok\\s+jarvis|jarvis|हे\\s*जार्विस|जार्विस"), "")
                                .trim(' ', ',', '.', ':')
                            if (stripped.length >= 2) {
                                _captureState.value = VoiceCaptureState.IDLE
                                onCommandRecognized(stripped)
                            } else {
                                onWakeTriggered()
                                startListening(isWakeMode = false)
                            }
                            return
                        } else if (wakeModeActive) {
                            _captureState.value = VoiceCaptureState.IDLE
                            scheduleWakeRestart(300L)
                            return
                        }
                    } else {
                        _captureState.value = VoiceCaptureState.IDLE
                        onCommandRecognized(transcript)
                        return
                    }
                }

                _captureState.value = VoiceCaptureState.IDLE
                if (wakeModeActive) {
                    scheduleWakeRestart(400L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    _partialTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocaleTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _captureState.value = VoiceCaptureState.IDLE
            onVoiceError("Microphone capture failed: ${e.localizedMessage}")
        }
    }

    private fun scheduleWakeRestart(delayMs: Long) {
        restartWakeJob?.cancel()
        restartWakeJob = scope.launch {
            delay(delayMs)
            if (wakeModeActive && _captureState.value == VoiceCaptureState.IDLE) {
                startListening(isWakeMode = true)
            }
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun updateMicWaveform(level: Float) {
        val t = System.currentTimeMillis() / 90.0
        _waveformBands.value = List(BAND_COUNT) { idx ->
            val harmonic = 0.55f + 0.45f * sin(t + idx * 0.55).toFloat()
            (level * harmonic).coerceIn(0.08f, 1.0f)
        }
    }

    private fun startSpeechWaveAnimation() {
        ttsWaveJob?.cancel()
        ttsWaveJob = scope.launch {
            var phase = 0f
            while (isActive) {
                phase += 0.35f
                _waveformBands.value = List(BAND_COUNT) { idx ->
                    val wave = 0.42f + 0.38f * sin(phase + idx * 0.48f) + 0.16f * sin(phase * 1.7f - idx * 0.3f)
                    wave.coerceIn(0.12f, 0.96f)
                }
                delay(55L)
            }
        }
    }

    private fun stopSpeechWaveAnimation() {
        ttsWaveJob?.cancel()
        ttsWaveJob = null
        resetWaveform()
    }

    private fun resetWaveform() {
        _waveformBands.value = List(BAND_COUNT) { 0.08f }
    }

    fun playTimerAlarm() {
        stopAlarm()
        alarmJob = scope.launch(Dispatchers.Default) {
            try {
                val generator = ToneGenerator(AudioManager.STREAM_ALARM, 85)
                toneGenerator = generator
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                repeat(6) {
                    if (!isActive) return@launch
                    generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 320)
                    vibrator?.let { vib ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vib.vibrate(VibrationEffect.createOneShot(220L, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vib.vibrate(220L)
                        }
                    }
                    delay(650L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Alarm playback error: ${e.message}")
            }
        }
    }

    fun stopAlarm() {
        alarmJob?.cancel()
        alarmJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {
        }
        toneGenerator = null
    }

    fun release() {
        stopAllVoiceActivity()
        stopAlarm()
        tts?.shutdown()
        tts = null
    }
}
