package com.example.data

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class HudMode(val displayName: String, val shortTag: String, val directive: String) {
    STANDARD(
        displayName = "Standard Command",
        shortTag = "CMD",
        directive = "Balanced tactical operations, proactive planning, and autonomous tool execution."
    ),
    CODING(
        displayName = "Coding/Dev Mode",
        shortTag = "DEV",
        directive = "Engineering focus: terse syntax, clean code blocks, architecture audits, and debugging."
    ),
    FOCUS(
        displayName = "Focus/Stealth Mode",
        shortTag = "STL",
        directive = "Low-distraction stealth telemetry: ultra-concise answers, deep work protection."
    ),
    DIAGNOSTIC(
        displayName = "Diagnostic Mode",
        shortTag = "DIAG",
        directive = "Full system telemetry inspection, latency tracing, optical scan & error forensics."
    );

    companion object {
        fun fromNameOrLabel(raw: String): HudMode {
            val normalized = raw.trim().lowercase()
            return entries.firstOrNull {
                it.name.lowercase() == normalized ||
                    it.displayName.lowercase() == normalized ||
                    normalized.contains(it.shortTag.lowercase()) ||
                    (normalized.contains("code") || normalized.contains("dev")) && it == CODING ||
                    (normalized.contains("focus") || normalized.contains("stealth")) && it == FOCUS ||
                    normalized.contains("diag") && it == DIAGNOSTIC
            } ?: STANDARD
        }
    }
}

enum class TaskPriority(val label: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL");

    companion object {
        fun safeFrom(raw: String): TaskPriority {
            val up = raw.trim().uppercase()
            return entries.firstOrNull { it.name == up } ?: MEDIUM
        }
    }
}

enum class WriterDocType(val label: String) {
    EMAIL("EMAIL"),
    CODE("CODE"),
    REPORT("REPORT"),
    SCRIPT("SCRIPT"),
    NOTE("NOTE");

    companion object {
        fun safeFrom(raw: String): WriterDocType {
            val up = raw.trim().uppercase()
            return entries.firstOrNull { it.name == up } ?: NOTE
        }
    }
}

@Keep
@IgnoreExtraProperties
data class MemoryNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "Preference",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PriorityTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val priority: String = TaskPriority.MEDIUM.name,
    val completed: Boolean = false,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomMacro(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val voiceTrigger: String = "",
    val promptCommand: String = "",
    val iconKey: String = "bolt",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WriterDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val docType: String = WriterDocType.NOTE.name,
    val tone: String = "Technical",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ActiveTimer(
    val id: String,
    val label: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val running: Boolean = true,
    val ringing: Boolean = false
)

enum class MessageRole {
    USER,
    JARVIS,
    TOOL,
    ERROR
}

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val timestampLabel: String,
    val attachmentNames: List<String> = emptyList(),
    val streaming: Boolean = false
)

data class AttachmentItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val bitmap: Bitmap? = null,
    val rawBytes: ByteArray? = null,
    val textPreview: String? = null
)

data class ToolToast(
    val id: String,
    val toolName: String,
    val summary: String,
    val isAlert: Boolean = false
)

data class VoiceSettings(
    val ttsPitch: Float = 0.94f,
    val ttsRate: Float = 1.04f,
    val ttsMuted: Boolean = false,
    val speechLocaleTag: String = "en-IN",
    val wakeListenerEnabled: Boolean = false
)

data class TelemetrySnapshot(
    val clockTime: String = "--:--:--",
    val dateLabel: String = "---",
    val batteryPercent: Int = 100,
    val batteryCharging: Boolean = false,
    val networkLabel: String = "ONLINE",
    val networkOnline: Boolean = true,
    val neuralLoadPercent: Int = 18,
    val apiLatencyMs: Long = 0L,
    val ramUsagePercent: Int = 32
)
