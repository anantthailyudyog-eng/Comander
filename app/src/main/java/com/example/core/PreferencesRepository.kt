package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.VoiceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_hud_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_MUTED = booleanPreferencesKey("tts_muted")
        val SPEECH_LOCALE = stringPreferencesKey("speech_locale")
        val WAKE_LISTENER = booleanPreferencesKey("wake_listener")
    }

    val voiceSettings: Flow<VoiceSettings> = context.jarvisDataStore.data.map { prefs ->
        VoiceSettings(
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 0.94f,
            ttsRate = prefs[Keys.TTS_RATE] ?: 1.04f,
            ttsMuted = prefs[Keys.TTS_MUTED] ?: false,
            speechLocaleTag = prefs[Keys.SPEECH_LOCALE] ?: "en-IN",
            wakeListenerEnabled = prefs[Keys.WAKE_LISTENER] ?: false
        )
    }

    suspend fun updateVoiceSettings(transform: (VoiceSettings) -> VoiceSettings) {
        context.jarvisDataStore.edit { prefs ->
            val current = VoiceSettings(
                ttsPitch = prefs[Keys.TTS_PITCH] ?: 0.94f,
                ttsRate = prefs[Keys.TTS_RATE] ?: 1.04f,
                ttsMuted = prefs[Keys.TTS_MUTED] ?: false,
                speechLocaleTag = prefs[Keys.SPEECH_LOCALE] ?: "en-IN",
                wakeListenerEnabled = prefs[Keys.WAKE_LISTENER] ?: false
            )
            val updated = transform(current)
            prefs[Keys.TTS_PITCH] = updated.ttsPitch.coerceIn(0.5f, 1.8f)
            prefs[Keys.TTS_RATE] = updated.ttsRate.coerceIn(0.6f, 2.0f)
            prefs[Keys.TTS_MUTED] = updated.ttsMuted
            prefs[Keys.SPEECH_LOCALE] = updated.speechLocaleTag
            prefs[Keys.WAKE_LISTENER] = updated.wakeListenerEnabled
        }
    }
}
