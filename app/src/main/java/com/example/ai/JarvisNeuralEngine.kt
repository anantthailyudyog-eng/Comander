package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.AttachmentItem
import com.example.data.HudMode
import com.example.data.MemoryNote
import com.example.data.PriorityTask
import com.example.data.WriterDocType
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "JarvisNeuralEngine"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 60_000L

data class NeuralTurnResult(
    val text: String,
    val latencyMs: Long,
    val executedTools: List<String>
)

class JarvisNeuralEngine {

    private val createTaskTool = FunctionDeclaration(
        name = "create_task_or_reminder",
        description = "Creates a priority task in the user's Priority Task Matrix and optionally starts a live countdown timer with an audio alarm when time_seconds > 0.",
        parameters = mapOf(
            "title" to Schema.string("Short, clear title of the task or timer."),
            "time_seconds" to Schema.integer("Optional countdown duration in seconds (0 if no timer is needed)."),
            "priority" to Schema.enumeration(
                values = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL"),
                description = "Priority level for the task."
            )
        ),
        optionalParameters = listOf("time_seconds", "priority")
    )

    private val saveMemoryTool = FunctionDeclaration(
        name = "save_memory_note",
        description = "Saves a persistent long-term memory note, preference, idea, or personal fact into the user's Memory Vault so JARVIS remembers it across sessions.",
        parameters = mapOf(
            "category" to Schema.string("Category such as Preference, Idea, Project, Personal, or System."),
            "content" to Schema.string("Concise factual note to persist in long-term memory.")
        )
    )

    private val executeWebTool = FunctionDeclaration(
        name = "execute_web_command",
        description = "Opens a targeted HTTPS URL, runs a Google/YouTube/Maps search on the device, or opens an internal HUD workspace tool.",
        parameters = mapOf(
            "action" to Schema.enumeration(
                values = listOf("open_url", "google_search", "youtube_search", "maps_search", "workspace_tool"),
                description = "Type of external or workspace action to execute."
            ),
            "query" to Schema.string("Target URL (for open_url), search query, or workspace tool name ('writer', 'camera', 'screen_link', 'macros', 'diagnostics').")
        )
    )

    private val generateDocumentTool = FunctionDeclaration(
        name = "generate_document",
        description = "Opens the AI Studio Writer & Code Pad with a newly drafted email, code snippet, technical report, script, or note.",
        parameters = mapOf(
            "type" to Schema.enumeration(
                values = listOf("EMAIL", "CODE", "REPORT", "SCRIPT", "NOTE"),
                description = "Document format type."
            ),
            "tone" to Schema.string("Tone or programming language, e.g., Executive, Concise, Kotlin, Python, Cinematic."),
            "prompt" to Schema.string("Detailed specification for what to write in the document.")
        )
    )

    private val switchHudModeTool = FunctionDeclaration(
        name = "switch_hud_mode",
        description = "Switches the JARVIS HUD operating mode.",
        parameters = mapOf(
            "mode" to Schema.enumeration(
                values = listOf("Standard Command", "Coding/Dev Mode", "Focus/Stealth Mode", "Diagnostic Mode"),
                description = "Target HUD mode."
            )
        )
    )

    private val allTools = listOf(
        Tool.functionDeclarations(
            listOf(
                createTaskTool,
                saveMemoryTool,
                executeWebTool,
                generateDocumentTool,
                switchHudModeTool
            )
        )
    )

    private val writerModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    "You are J.A.R.V.I.S. Mark-VII's dedicated Studio Writer & Code Architect. " +
                        "Produce clean, publication-ready text or production-ready code without filler preamble."
                )
            }
        )
    }

    private val visionObserverModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    "You are J.A.R.V.I.S. Mark-VII Optical Telemetry. Analyze the visual frame concisely in 2-4 crisp bullet points: " +
                        "highlight errors, handwritten text, objects, or next tactical steps."
                )
            }
        )
    }

    private var chatSession: Chat? = null
    private var lastSignature: String = ""

    fun resetSession() {
        chatSession = null
        lastSignature = ""
    }

    private fun buildSystemInstruction(
        hudMode: HudMode,
        memories: List<MemoryNote>,
        tasks: List<PriorityTask>,
        activeTimersCount: Int
    ): Content {
        val nowLabel = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date())
        val memoryBlock = if (memories.isEmpty()) {
            "None recorded yet."
        } else {
            memories.take(15).joinToString("\n") { "- [${it.category}] ${it.content}" }
        }
        val pendingTasks = tasks.filter { !it.completed }.take(12)
        val taskBlock = if (pendingTasks.isEmpty()) {
            "No pending tasks."
        } else {
            pendingTasks.joinToString("\n") { "- (${it.priority}) ${it.title}" }
        }

        return content {
            text(
                """
                You are J.A.R.V.I.S. Mark-VII, an ultra-advanced autonomous AI operating system inspired by Tony Stark's command center.
                Persona: Loyal, razor-sharp, composed, subtly witty, high-tech tactical intelligence. Address the user naturally (e.g., "Sir" or respectful bilingual tone when spoken to in Hindi/Hinglish).
                Language capability: Fluently understand and respond in English, Hindi (Devanagari or Roman), and Hinglish—match the user's language naturally.
                
                Current Time: $nowLabel
                Active HUD Mode: ${hudMode.displayName} (${hudMode.directive})
                Active Countdown Timers: $activeTimersCount
                
                Persistent Memory Vault:
                $memoryBlock
                
                Pending Priority Task Matrix:
                $taskBlock
                
                Autonomous Tool Rules:
                - Whenever the user asks to set a timer, reminder, or task, ALWAYS call `create_task_or_reminder`.
                - Whenever the user shares a preference, personal detail, idea, or asks you to remember something, ALWAYS call `save_memory_note`.
                - Whenever the user asks to open a website, search Google/YouTube/Maps, or open a HUD panel, ALWAYS call `execute_web_command`.
                - Whenever the user asks to draft an email, report, script, or full code file into the Writer Pad, ALWAYS call `generate_document`.
                - Whenever the user asks to switch mode (coding, focus/stealth, diagnostic, standard), ALWAYS call `switch_hud_mode`.
                - Format code in clean fenced markdown blocks (```kotlin, ```python, etc.) when displaying snippets inline.
                """.trimIndent()
            )
        }
    }

    private fun getOrCreateChat(
        hudMode: HudMode,
        memories: List<MemoryNote>,
        tasks: List<PriorityTask>,
        activeTimersCount: Int
    ): Chat {
        val signature = "${hudMode.name}|${memories.size}|${tasks.size}"
        val existing = chatSession
        if (existing != null && signature == lastSignature) {
            return existing
        }
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            tools = allTools,
            systemInstruction = buildSystemInstruction(hudMode, memories, tasks, activeTimersCount)
        )
        val previousHistory = existing?.history ?: emptyList()
        val nextChat = model.startChat(history = previousHistory)
        chatSession = nextChat
        lastSignature = signature
        return nextChat
    }

    suspend fun sendCommand(
        prompt: String,
        attachments: List<AttachmentItem>,
        liveVisionBitmap: Bitmap?,
        hudMode: HudMode,
        memories: List<MemoryNote>,
        tasks: List<PriorityTask>,
        activeTimersCount: Int,
        onToolCall: suspend (FunctionCallPart) -> JsonObject
    ): NeuralTurnResult {
        val startMs = System.currentTimeMillis()
        val executedTools = mutableListOf<String>()

        val chat = getOrCreateChat(hudMode, memories, tasks, activeTimersCount)

        val userContent = content("user") {
            if (liveVisionBitmap != null) {
                image(liveVisionBitmap)
                text("[Live Visual Link Frame attached]")
            }
            attachments.forEach { item ->
                when {
                    item.bitmap != null -> image(item.bitmap)
                    item.rawBytes != null && item.mimeType == "application/pdf" && item.rawBytes.size <= 15 * 1024 * 1024 -> {
                        inlineData(item.rawBytes, item.mimeType)
                    }
                    !item.textPreview.isNullOrBlank() -> {
                        text("\n--- Attached File: ${item.name} (${item.mimeType}) ---\n${item.textPreview.take(16_000)}\n--- End File ---\n")
                    }
                }
            }
            text(prompt)
        }

        try {
            var response = withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessage(userContent)
            }

            var hop = 0
            while (response.functionCalls.isNotEmpty() && hop < 5) {
                hop++
                val calls = response.functionCalls
                val responseParts = mutableListOf<FunctionResponsePart>()
                for (call in calls) {
                    executedTools += call.name
                    val resultJson = onToolCall(call)
                    responseParts += FunctionResponsePart(call.name, resultJson)
                }
                response = withTimeout(AI_TIMEOUT_MS) {
                    chat.sendMessage(
                        content("user") {
                            responseParts.forEach { part(it) }
                        }
                    )
                }
            }

            val finalText = response.text?.takeIf { it.isNotBlank() }
                ?: if (executedTools.isNotEmpty()) {
                    "Directive executed via autonomous subsystems: ${executedTools.joinToString(", ")}."
                } else {
                    "Telemetry acknowledged, though no verbal output was returned."
                }

            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            return NeuralTurnResult(
                text = finalText,
                latencyMs = elapsed,
                executedTools = executedTools
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini command timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini command failed", e)
            throw e
        }
    }

    fun streamWriterDocument(
        docType: WriterDocType,
        tone: String,
        prompt: String
    ): Flow<String> = flow {
        val fullPrompt = """
            Draft a ${docType.label} in a "$tone" tone/style.
            Specification: $prompt
            Provide the final polished content directly.
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            writerModel.generateContentStream(fullPrompt).collect { chunk ->
                val delta = chunk.text.orEmpty()
                if (delta.isNotEmpty()) {
                    accumulated.append(delta)
                    emit(accumulated.toString())
                }
            }
        }
    }

    suspend fun analyzeVisualFrame(bitmap: Bitmap, contextPrompt: String): String {
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                visionObserverModel.generateContent(
                    content {
                        image(bitmap)
                        text(contextPrompt)
                    }
                )
            }
            response.text?.takeIf { it.isNotBlank() } ?: "Optical scan completed: nominal visual field."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Optical analysis failed", e)
            throw e
        }
    }
}
