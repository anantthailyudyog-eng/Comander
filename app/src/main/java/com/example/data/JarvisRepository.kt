package com.example.data

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUid != null && uid != expectedUid) {
            throw IllegalStateException("Signed-in user changed; refusing cross-user access.")
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeMemories(userId: String): Flow<List<MemoryNote>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/memories"
        emitAll(
            userDoc(userId).collection("memories")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MemoryNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTasks(userId: String): Flow<List<PriorityTask>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/tasks"
        emitAll(
            userDoc(userId).collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PriorityTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMacros(userId: String): Flow<List<CustomMacro>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/macros"
        emitAll(
            userDoc(userId).collection("macros")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CustomMacro::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDocuments(userId: String): Flow<List<WriterDocument>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/documents"
        emitAll(
            userDoc(userId).collection("documents")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WriterDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMemory(userId: String, category: String, content: String): Result<String> {
        val cleanCategory = category.trim().ifBlank { "General" }.take(40)
        val cleanContent = content.trim().take(2000)
        var docPath = "users/$userId/memories"
        return try {
            require(cleanContent.isNotEmpty()) { "Memory content cannot be empty." }
            val uid = requireUserId(userId)
            val docRef = userDoc(uid).collection("memories").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "category" to cleanCategory,
                "content" to cleanContent,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMemory(userId: String, memoryId: String, category: String, content: String): Result<Unit> {
        val cleanCategory = category.trim().ifBlank { "General" }.take(40)
        val cleanContent = content.trim().take(2000)
        val docPath = "users/$userId/memories/$memoryId"
        return try {
            require(memoryId.isNotBlank() && cleanContent.isNotEmpty()) { "Invalid memory update input." }
            val uid = requireUserId(userId)
            userDoc(uid).collection("memories").document(memoryId).update(
                mapOf(
                    "category" to cleanCategory,
                    "content" to cleanContent,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String): Result<Unit> {
        val docPath = "users/$userId/memories/$memoryId"
        return try {
            require(memoryId.isNotBlank())
            val uid = requireUserId(userId)
            userDoc(uid).collection("memories").document(memoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addTask(
        userId: String,
        title: String,
        priority: TaskPriority,
        durationSeconds: Long = 0L
    ): Result<String> {
        val cleanTitle = title.trim().take(200)
        val boundedDuration = durationSeconds.coerceIn(0L, 86400L)
        var docPath = "users/$userId/tasks"
        return try {
            require(cleanTitle.isNotEmpty()) { "Task title cannot be empty." }
            val uid = requireUserId(userId)
            val docRef = userDoc(uid).collection("tasks").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "priority" to priority.name,
                "completed" to false,
                "durationSeconds" to boundedDuration,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(userId: String, taskId: String, completed: Boolean): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            require(taskId.isNotBlank())
            val uid = requireUserId(userId)
            userDoc(uid).collection("tasks").document(taskId).update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            require(taskId.isNotBlank())
            val uid = requireUserId(userId)
            userDoc(uid).collection("tasks").document(taskId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMacro(
        userId: String,
        name: String,
        voiceTrigger: String,
        promptCommand: String,
        iconKey: String = "bolt"
    ): Result<String> {
        val cleanName = name.trim().take(60)
        val cleanTrigger = voiceTrigger.trim().take(80)
        val cleanPrompt = promptCommand.trim().take(1500)
        val cleanIcon = iconKey.trim().ifBlank { "bolt" }.take(30)
        var docPath = "users/$userId/macros"
        return try {
            require(cleanName.isNotEmpty() && cleanPrompt.isNotEmpty()) {
                "Macro name and command prompt are required."
            }
            val uid = requireUserId(userId)
            val docRef = userDoc(uid).collection("macros").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "voiceTrigger" to cleanTrigger,
                "promptCommand" to cleanPrompt,
                "iconKey" to cleanIcon,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMacro(userId: String, macroId: String): Result<Unit> {
        val docPath = "users/$userId/macros/$macroId"
        return try {
            require(macroId.isNotBlank())
            val uid = requireUserId(userId)
            userDoc(uid).collection("macros").document(macroId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveDocument(
        userId: String,
        title: String,
        docType: WriterDocType,
        tone: String,
        content: String
    ): Result<String> {
        val cleanTitle = title.trim().ifBlank { "Untitled Draft" }.take(120)
        val cleanTone = tone.trim().ifBlank { "Technical" }.take(40)
        val cleanContent = content.take(20000)
        var docPath = "users/$userId/documents"
        return try {
            val uid = requireUserId(userId)
            val docRef = userDoc(uid).collection("documents").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "docType" to docType.name,
                "tone" to cleanTone,
                "content" to cleanContent,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDocument(
        userId: String,
        docId: String,
        title: String,
        docType: WriterDocType,
        tone: String,
        content: String
    ): Result<Unit> {
        val cleanTitle = title.trim().ifBlank { "Untitled Draft" }.take(120)
        val cleanTone = tone.trim().ifBlank { "Technical" }.take(40)
        val cleanContent = content.take(20000)
        val docPath = "users/$userId/documents/$docId"
        return try {
            require(docId.isNotBlank())
            val uid = requireUserId(userId)
            userDoc(uid).collection("documents").document(docId).update(
                mapOf(
                    "title" to cleanTitle,
                    "docType" to docType.name,
                    "tone" to cleanTone,
                    "content" to cleanContent,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(userId: String, docId: String): Result<Unit> {
        val docPath = "users/$userId/documents/$docId"
        return try {
            require(docId.isNotBlank())
            val uid = requireUserId(userId)
            userDoc(uid).collection("documents").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
