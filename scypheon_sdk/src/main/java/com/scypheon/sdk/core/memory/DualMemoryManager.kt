package com.scypheon.sdk.core.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.os.Build
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.security.ZeroKnowledgeEnclave
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val status: Int = ScypheonDbHelper.STATUS_SUCCESS,
    val isContextEligible: Boolean = true
)

data class Session(val id: String, val title: String, val timestamp: Long)

@Singleton
class DualMemoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vectorManager: IVectorEngine,
    private val graphManager: GraphMemoryManager
) {
    // Solaris Hardening: Mutex to prevent race conditions during DB writes
    private val dbLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getContext(): Context = context

    suspend fun addFact(subject: String, predicate: String, obj: String, confidence: Float = 1.0f) {
        graphManager.addFact(subject, predicate, obj, confidence)
    }

    fun saveFact(subject: String, relation: String, obj: String) {
        scope.launch {
            addFact(subject, relation, obj)
        }
    }

    suspend fun getMemoryContext(query: String): String {
        val facts = graphManager.querySubject(query)
        return if (facts.isEmpty()) "No direct facts found." else facts.joinToString("; ")
    }

    private val dbHelper = ScypheonDbHelper(context)
    private val enclave = ZeroKnowledgeEnclave(context)

    // --- Transactional ACID Writes ---

    /**
     * One-time startup cleanup: Delete all persisted engine error messages from the DB.
     * Before the MEMORY GUARD fix, error strings like "Error: AI engine disconnected..."
     * were saved as legitimate assistant responses. These must be purged to prevent
     * permanent prompt contamination causing the infinite generation loop.
     */
    suspend fun purgeEngineErrorMessages(): Int = withContext(Dispatchers.IO) {
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            var purgedCount = 0
            try {
                val idsToDelete = mutableListOf<Long>()
                db.rawQuery(
                    "SELECT id, text FROM ${ScypheonDbHelper.TABLE_MESSAGES} WHERE is_user = 0",
                    null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val rawText = cursor.getString(1)
                        val decrypted = try { enclave.decryptData(rawText) } catch (e: Exception) { "" }
                        if (decrypted.startsWith("Error:") || decrypted.isBlank()) {
                            idsToDelete.add(id)
                        }
                    }
                }
                
                if (idsToDelete.isNotEmpty()) {
                    val cappedIds = idsToDelete.take(500)
                    
                    // [v1.2.0-SAR] PHOENIX RECOVERY: Perform deletion in a hardened transaction
                    db.beginTransaction()
                    try {
                        // 🛡️ Temporarily drop triggers to bypass potential FTS index corruption during deletion
                        db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
                        
                        for (id in cappedIds) {
                            db.delete(ScypheonDbHelper.TABLE_MESSAGES, "id = ?", arrayOf(id.toString()))
                        }
                        
                        db.setTransactionSuccessful()
                        purgedCount = cappedIds.size
                    } finally {
                        if (db.inTransaction()) db.endTransaction()
                    }
                    
                    // 🛡️ Restore FTS integrity and triggers after the heavy delete
                    rebuildFts(db)
                    Timber.i("🛡️ [PHOENIX] Startup purge: Deleted $purgedCount corrupted messages and healed FTS index.")
                }
            } catch (e: Exception) {
                Timber.e(e, "🚨 [PHOENIX] Critical failure during message purge. Initiating Nuclear FTS Reset.")
                try {
                    rebuildFts(db)
                } catch (re: Exception) {
                    Timber.e(re, "Nuclear reset failed. System may be unstable.")
                }
            }
            purgedCount
        }
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                // 🛡️ [PHOENIX] Bypassing corrupt FTS triggers during nuclear reset
                db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
                db.execSQL("DROP TRIGGER IF EXISTS messages_ai")
                db.execSQL("DROP TRIGGER IF EXISTS messages_au")

                // Delete all data
                db.delete(ScypheonDbHelper.TABLE_SESSIONS, null, null)
                db.delete(ScypheonDbHelper.TABLE_MESSAGES, null, null)
                
                db.setTransactionSuccessful()
            } catch (e: Exception) {
                Timber.e(e, "Nuclear reset failed during deletion phase")
                throw e
            } finally {
                if (db.inTransaction()) db.endTransaction()
            }

            // 🛡️ Force a clean rebuild of the FTS index and triggers
            rebuildFts(db)
            Timber.i("🛡️ [PHOENIX] Nuclear reset successful. All memories purged and index healed.")
        }
    }

    /**
     * Enterprise Protocol: Database TTL Sweep (AGENTS.md Section 3)
     * Purge historical messages and sessions older than the specified timestamp.
     */
    suspend fun performTtlSweep(thirtyDaysAgo: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Cascade delete will handle messages if we delete the session
            // but we might want to keep sessions and just prune old messages.
            // For now, we delete entire sessions older than 30 days.
            db.delete(ScypheonDbHelper.TABLE_SESSIONS, "timestamp < ?", arrayOf(thirtyDaysAgo.toString()))
            
            // Also ensure orphaned messages (if any) are cleared
            db.delete(ScypheonDbHelper.TABLE_MESSAGES, "timestamp < ?", arrayOf(thirtyDaysAgo.toString()))
            
            db.setTransactionSuccessful()
            Timber.i("TTL Sweep: Purged data older than $thirtyDaysAgo")
        } catch (e: Exception) {
            Timber.e(e, "TTL Sweep Failed")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Replaces the oldest N raw messages with a single summarized block.
     * Prevents LLM context window limits and OOM crashes.
     */
    suspend fun replaceMessagesWithSummary(sessionId: String, countToReplace: Int, summaryText: String) = withContext(Dispatchers.IO) {
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val idsToDelete = mutableListOf<Long>()
                db.rawQuery(
                    "SELECT id FROM ${ScypheonDbHelper.TABLE_MESSAGES} WHERE session_id = ? ORDER BY timestamp ASC LIMIT ?",
                    arrayOf(sessionId, countToReplace.toString())
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        idsToDelete.add(cursor.getLong(0))
                    }
                }

                if (idsToDelete.isEmpty()) {
                    db.setTransactionSuccessful()
                    return@withLock
                }

                // [v1.1.4-SAR] Drop FTS triggers BEFORE deletion to prevent
                // SQL logic error from corrupt FTS index during DELETE cascade.
                db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
                db.execSQL("DROP TRIGGER IF EXISTS messages_ai")
                db.execSQL("DROP TRIGGER IF EXISTS messages_au")

                for (id in idsToDelete) {
                    db.delete(ScypheonDbHelper.TABLE_MESSAGES, "id = ?", arrayOf(id.toString()))
                }

                val encryptedSummary = enclave.encryptData(summaryText)
                val values = ContentValues().apply {
                    put("session_id", sessionId)
                    put("text", encryptedSummary)
                    put("is_user", 0)
                    put("timestamp", System.currentTimeMillis() - 1000000L)
                    put("status", ScypheonDbHelper.STATUS_SUCCESS)
                    put("is_context_eligible", 1)
                }
                db.insert(ScypheonDbHelper.TABLE_MESSAGES, null, values)

                db.setTransactionSuccessful()
                Timber.i("[v1.0.3-SAR] Replaced ${idsToDelete.size} raw messages with 1 summary block.")
            } catch (e: Exception) {
                Timber.e(e, "[v1.0.3-SAR] Failed to replace messages with summary.")
            } finally {
                if (db.inTransaction()) db.endTransaction()
            }

            // Always rebuild FTS after trigger-bypassed operations
            rebuildFts(db)
        }
    }

    /**
     * [v1.1.5-SAR] Long-Term Memory Bridge.
     * Takes a condensed summary and indexes it in the Vector DB for permanent retrieval.
     * This allows future sessions to "remember" this context via Semantic RAG search.
     */
    suspend fun saveSummaryToLongTerm(sessionId: String, summary: String) = withContext(Dispatchers.IO) {
        dbLock.withLock {
            try {
                // [Ide 3] Pure text embedding for maximum semantic accuracy
                val embedding = vectorManager.embedText(summary) ?: return@withLock
                val db = dbHelper.writableDatabase
                val encryptedSummary = enclave.encryptData(summary)
                
                val values = ContentValues().apply {
                    put("session_id", sessionId)
                    put("text", encryptedSummary)
                    put("is_user", 0)
                    put("timestamp", System.currentTimeMillis())
                    put("is_context_eligible", 0) // Indexed for RAG, but hidden from the normal Chat UI
                    put("status", ScypheonDbHelper.STATUS_SUCCESS)
                    put("embedding", embeddingToBlob(embedding))
                }
                db.insert(ScypheonDbHelper.TABLE_MESSAGES, null, values)
                Timber.i(" [SAR] Pure summary indexed in Long-Term Memory.")
            } catch (e: Exception) {
                Timber.e(e, " [SAR] Failed to save summary to Long-Term Memory")
            }
        }
    }

    private fun embeddingToBlob(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (v in embedding) buffer.putFloat(v)
        return buffer.array()
    }


    /**
     * Enterprise Privacy Feature: Zero-Trust Wiping.
     * Securely deletes all messages and embeddings associated with a volatile session.
     */
    fun wipeSessionMemory(sessionId: String) {
        val db = dbHelper.writableDatabase
        try {
            db.delete(ScypheonDbHelper.TABLE_MESSAGES, "session_id = ?", arrayOf(sessionId))
            db.delete(ScypheonDbHelper.TABLE_SESSIONS, "id = ?", arrayOf(sessionId))
            Timber.w("Zero-Trust: Wiped volatile session memory ($sessionId)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to wipe session memory")
        }
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", title)
        }
        db.update(ScypheonDbHelper.TABLE_SESSIONS, values, "id = ?", arrayOf(sessionId))
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(ScypheonDbHelper.TABLE_MESSAGES, "session_id = ?", arrayOf(sessionId))
            db.delete(ScypheonDbHelper.TABLE_SESSIONS, "id = ?", arrayOf(sessionId))
            db.setTransactionSuccessful()
            Timber.i("Session $sessionId deleted successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete session")
        } finally {
            db.endTransaction()
        }
    }

    suspend fun archiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.rawQuery("SELECT title FROM ${ScypheonDbHelper.TABLE_SESSIONS} WHERE id = ?", arrayOf(sessionId)).use { cursor ->
            if (cursor.moveToFirst()) {
                val currentTitle = cursor.getString(0)
                if (!currentTitle.startsWith("[ARCHIVED]")) {
                    val values = ContentValues().apply {
                        put("title", "[ARCHIVED] $currentTitle")
                    }
                    db.update(ScypheonDbHelper.TABLE_SESSIONS, values, "id = ?", arrayOf(sessionId))
                }
            }
        }
    }

    suspend fun unarchiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.rawQuery("SELECT title FROM ${ScypheonDbHelper.TABLE_SESSIONS} WHERE id = ?", arrayOf(sessionId)).use { cursor ->
            if (cursor.moveToFirst()) {
                val currentTitle = cursor.getString(0)
                if (currentTitle.startsWith("[ARCHIVED] ")) {
                    val values = ContentValues().apply {
                        put("title", currentTitle.removePrefix("[ARCHIVED] "))
                    }
                    db.update(ScypheonDbHelper.TABLE_SESSIONS, values, "id = ?", arrayOf(sessionId))
                }
            }
        }
    }

    /**
     * Expires "zombie" tasks that have been awaiting approval for too long.
     */
    fun expireAwaitingApprovalTasks(fifteenMinsAgo: Long) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Any message starting with [AWAITING_APPROVAL] that is too old
            db.delete(ScypheonDbHelper.TABLE_MESSAGES, 
                "text LIKE '[AWAITING_APPROVAL]%' AND timestamp < ?", 
                arrayOf(fifteenMinsAgo.toString()))
            
            db.setTransactionSuccessful()
            Timber.i("Expired zombie AWAITING_APPROVAL tasks older than $fifteenMinsAgo")
        } catch (e: Exception) {
            Timber.e(e, "Failed to expire zombie tasks")
        } finally {
            db.endTransaction()
        }
    }

    suspend fun createSession(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", sessionId)
                put("title", title)
                put("timestamp", System.currentTimeMillis())
            }
            db.insertWithOnConflict(ScypheonDbHelper.TABLE_SESSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Timber.e(e, "Failed to create session")
        } finally {
            db.endTransaction()
        }
    }



    suspend fun saveMessage(
        sessionId: String, 
        text: String, 
        isUser: Boolean, 
        status: Int = ScypheonDbHelper.STATUS_SUCCESS,
        isContextEligible: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        var finalMessageId = -1L
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val encryptedText = enclave.encryptData(text)
                val values = ContentValues().apply {
                    put("session_id", sessionId)
                    put("text", encryptedText)
                    put("is_user", if (isUser) 1 else 0)
                    put("timestamp", System.currentTimeMillis())
                    put("status", status)
                    put("is_context_eligible", if (isContextEligible) 1 else 0)
                }
                finalMessageId = db.insert(ScypheonDbHelper.TABLE_MESSAGES, null, values)
                db.setTransactionSuccessful()
                Timber.i("Successfully saved message record (ID=$finalMessageId, Status=$status)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save message record")
            } finally {
                db.endTransaction()
            }

            if (finalMessageId != -1L) {
                scope.launch {
                    try {
                        val embedding = vectorManager.embedText(text)
                        if (embedding != null && embedding.isNotEmpty()) {
                            val buffer = ByteBuffer.allocate(embedding.size * 4).apply {
                                order(ByteOrder.LITTLE_ENDIAN)
                                embedding.forEach { putFloat(it) }
                            }
                            
                            val dbAsync = dbHelper.writableDatabase
                            val updateValues = ContentValues().apply {
                                put("embedding", buffer.array())
                            }
                            dbAsync.update(ScypheonDbHelper.TABLE_MESSAGES, updateValues, "id = ?", arrayOf(finalMessageId.toString()))
                            Timber.d("Async pure embedding attached for message $finalMessageId")
                        }
                    } catch (e: Exception) {
                        Timber.w("Async embedding failed for message $finalMessageId: ${e.message}")
                    }

                    // [v1.5.0-SAR] Run LLM-driven fact extraction on ALL messages (user + assistant).
                    // Claude Code pattern: extractMemories runs a forked agent after each turn.
                    // Scypheon pattern: routeRequest with a structured extraction prompt.
                    extractAndAnchorFacts(text, isUser)
                }
            }
        }
        finalMessageId
    }

    /**
     * [v1.5.0-SAR] LLM-Driven Semantic Fact Extraction.
     * 
     * Ported from Claude Code's extractMemories service (src/services/extractMemories/).
     * Instead of brittle regex patterns, we ask the LLM itself to extract facts.
     * 
     * Claude Code architecture:
     * - Runs a "forked agent" (background LLM call) after each turn
     * - The LLM reads the conversation and writes memories to files
     * - Uses FileWrite/FileEdit tools to persist facts
     * 
     * Scypheon adaptation:
     * - Uses routeRequest() for a lightweight single-shot extraction prompt
     * - LLM outputs structured JSON triplets: [{"s":"user","p":"likes","o":"dragon fruit"}]
     * - Facts are stored in the SQLite Knowledge Graph (not files)
     * - Runs on BOTH user and assistant messages
     * - Supports Indonesian (Bahasa) and English
     */
    private var gateway: NeuralGateway? = null
    
    fun setGateway(gw: NeuralGateway) { this.gateway = gw }

    private suspend fun extractAndAnchorFacts(text: String, isUser: Boolean) {
        // Skip very short messages — no meaningful facts in "ok", "hi", "ya"
        if (text.length < 15) return
        
        // Skip messages that are clearly system/error content
        if (text.startsWith("[") || text.startsWith("⚠") || text.startsWith("Error:")) return

        // [v1.5.1-SAR] Concurrency Hardening: Add delay to prevent resource contention.
        // Fact extraction uses background LLM routes. Under on-device mobile architectures,
        // running concurrent inference tasks causes resource lockouts, engine timeouts,
        // and cancellation cascades. We delay extraction until the main conversation flow is idle.
        if (isUser) {
            kotlinx.coroutines.delay(12000L) // Wait 12s for assistant generation to complete
        } else {
            kotlinx.coroutines.delay(4000L)  // Wait 4s for assistant UI streams to settle
        }

        val gw = gateway ?: run {
            Timber.w("[MEMORY] Gateway not set — falling back to heuristic extraction")
            extractFactsHeuristic(text, isUser)
            return
        }

        try {
            val extractionPrompt = buildString {
                append("You are a memory extraction agent. Analyze the following message and extract personal facts as structured knowledge triplets.\n\n")
                append("MESSAGE (from ${if (isUser) "USER" else "ASSISTANT"}):\n\"\"\"$text\"\"\"\n\n")
                append("RULES:\n")
                append("- Extract ONLY personal facts, preferences, relationships, allergies, or biographical info\n")
                append("- Subject should be \"user\" for facts about the user, or a person's name for facts about others\n")
                append("- Use simple predicates: likes, dislikes, loves, is allergic to, has, is, works as, lives in, prefers, studies, owns, fears, wants, needs\n")
                append("- Object should be the specific thing/person/place\n")
                append("- Support both Indonesian and English messages\n")
                append("- If NO personal facts exist in the message, respond with exactly: NONE\n")
                append("- If facts exist, respond with ONLY a JSON array, nothing else:\n")
                append("[{\"s\":\"user\",\"p\":\"likes\",\"o\":\"dragon fruit\"},{\"s\":\"user\",\"p\":\"loves\",\"o\":\"chloe\"}]\n")
            }

            // Fire-and-forget with timeout — don't block the main conversation
            kotlinx.coroutines.withTimeout(8000) {
                val sb = StringBuilder()
                gw.routeRequest(extractionPrompt, enableThinking = false)
                    .collect { chunk -> sb.append(chunk) }
                
                parseAndStoreFacts(sb.toString().trim())
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.w("[MEMORY] LLM extraction timed out — falling back to heuristic")
            extractFactsHeuristic(text, isUser)
        } catch (e: Exception) {
            Timber.e(e, "[MEMORY] LLM fact extraction failed — falling back to heuristic")
            extractFactsHeuristic(text, isUser)
        }
    }

    /**
     * Parses the LLM's structured JSON output and stores triplets in the Knowledge Graph.
     */
    private suspend fun parseAndStoreFacts(response: String) {
        if (response.equals("NONE", ignoreCase = true) || response.isBlank()) return
        
        try {
            // Extract JSON array from response (LLM might add explanation text around it)
            val jsonStart = response.indexOf('[')
            val jsonEnd = response.lastIndexOf(']')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) return
            
            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            val jsonArray = org.json.JSONArray(jsonStr)
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val subject = obj.optString("s", "").trim()
                val predicate = obj.optString("p", "").trim()
                val objectVal = obj.optString("o", "").trim()
                
                if (subject.isNotEmpty() && predicate.isNotEmpty() && objectVal.isNotEmpty()) {
                    // Check for conflicting facts before inserting
                    val conflict = graphManager.queryConflictingFact(subject, predicate, objectVal)
                    if (conflict != null) {
                        Timber.i("🔄 [MEMORY] Updating fact: [$subject] [$predicate] $conflict → $objectVal")
                    }
                    
                    graphManager.addFact(subject, predicate, objectVal)
                    Timber.i("🧠 [MEMORY] LLM extracted: [$subject] → [$predicate] → [$objectVal]")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[MEMORY] Failed to parse LLM extraction response: ${response.take(200)}")
        }
    }

    /**
     * Lightweight heuristic fallback when LLM is unavailable.
     * Catches only the most obvious patterns — the LLM path is the primary extractor.
     */
    private suspend fun extractFactsHeuristic(text: String, isUser: Boolean) {
        try {
            val lower = text.lowercase()
            
            // Indonesian patterns (user messages)
            val idPatterns = listOf(
                Regex("(?:aku|saya|gue|gw)\\s+(?:suka|senang|seneng|doyan)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "likes",
                Regex("(?:aku|saya|gue|gw)\\s+(?:benci|ga suka|gak suka|tidak suka|nggak suka)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "dislikes",
                Regex("(?:aku|saya|gue|gw)\\s+(?:cinta|sayang|naksir)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "loves",
                Regex("(?:aku|saya|gue|gw)\\s+(?:alergi|allergic)\\s+(?:sama|terhadap|dengan)?\\s*(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "is allergic to",
                Regex("(?:nama\\s+(?:aku|saya|gue|gw)|(?:aku|saya|gue|gw)\\s+nama(?:nya)?)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "name is",
                Regex("(?:aku|saya|gue|gw)\\s+(?:tinggal|domisili)\\s+(?:di)?\\s*(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "lives in",
                Regex("(?:aku|saya|gue|gw)\\s+(?:kerja|bekerja)\\s+(?:sebagai|jadi)?\\s*(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "works as",
                Regex("(?:aku|saya|gue|gw)\\s+(?:takut|fobia)\\s+(?:sama|dengan|terhadap)?\\s*(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "fears",
                Regex("(?:aku|saya|gue|gw)\\s+(?:punya|memiliki)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "has"
            )
            
            // English patterns (user messages)
            val enPatterns = listOf(
                Regex("(?:i|my)\\s+(?:like|love|enjoy|adore)s?\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "likes",
                Regex("(?:i|my)\\s+(?:hate|dislike|can't stand)s?\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "dislikes",
                Regex("(?:i'm|i am)\\s+(?:allergic to)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "is allergic to",
                Regex("my\\s+name\\s+is\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "name is",
                Regex("(?:i|i'm|i am)\\s+(?:afraid of|scared of)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "fears",
                Regex("(?:i)\\s+(?:live|stay)\\s+(?:in|at)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "lives in",
                Regex("(?:i)\\s+(?:work|working)\\s+(?:as|at)\\s+(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "works as",
                Regex("(?:i)\\s+(?:have|own)\\s+(?:a|an)?\\s*(.+?)(?:\\.|,|!|$)", RegexOption.IGNORE_CASE) to "has"
            )
            
            val patterns = if (isUser) idPatterns + enPatterns else emptyList()
            
            for ((regex, predicate) in patterns) {
                regex.find(lower)?.let { match ->
                    val objectVal = match.groupValues[1].trim()
                    if (objectVal.length in 2..50) { // Sanity check
                        graphManager.addFact("user", predicate, objectVal)
                        Timber.i("🧠 [MEMORY/HEURISTIC] Extracted: [user] → [$predicate] → [$objectVal]")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[MEMORY] Heuristic fact anchoring failed")
        }
    }

    /**
     * Updates the status of a specific message (e.g. from QUEUED to SUCCESS or FAILED).
     */
    suspend fun updateMessageStatus(id: Long, status: Int) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", status)
        }
        db.update(ScypheonDbHelper.TABLE_MESSAGES, values, "id = ?", arrayOf(id.toString()))
    }

    suspend fun updateUserAllergies(allergies: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val encryptedAllergies = enclave.encryptData(allergies)
        val values = ContentValues().apply {
            put("key", "allergies")
            put("value", encryptedAllergies)
        }
        db.insertWithOnConflict(ScypheonDbHelper.TABLE_PROFILE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // --- Fast Queries ---

    /**
     * Edge Max: Returns unified allergies from both Key-Value profile and Knowledge Graph.
     */
    suspend fun getUserAllergies(): String = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        var profileAllergies = "None recorded"
        db.rawQuery("SELECT value FROM ${ScypheonDbHelper.TABLE_PROFILE} WHERE key = 'allergies'", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val encryptedVal = cursor.getString(0)
                profileAllergies = enclave.decryptData(encryptedVal)
            }
        }

        val graphAllergies = graphManager.getAllergies()
        if (graphAllergies.contains("No known") || graphAllergies.isEmpty()) {
            profileAllergies
        } else {
            "$profileAllergies. Graph Deductions: $graphAllergies"
        }
    }

    /**
     * Retrieves the list of currently prescribed medicines from the Knowledge Graph.
     * Searches for facts where the predicate indicates medical consumption.
     */
    suspend fun getCurrentPrescriptions(): List<String> = withContext(Dispatchers.IO) {
        val facts = graphManager.queryByPredicate("takes_medicine") + 
                   graphManager.queryByPredicate("mengonsumsi")
        facts.map { it.third }.distinct()
    }


    /**
     * Retrieve all deeply connected Graph facts for a given entity.
     */
    suspend fun querySubject(entity: String): List<String> {
        return graphManager.querySubject(entity)
    }

    suspend fun getRawKnowledgeGraph(): List<Triple<String, String, String>> {
        return graphManager.getFullGraph()
    }

    suspend fun getAllSessions(): List<Session> = withContext(Dispatchers.IO) {
        val sessions = mutableListOf<Session>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT id, title, timestamp FROM ${ScypheonDbHelper.TABLE_SESSIONS} ORDER BY timestamp DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                sessions.add(Session(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
            }
        }
        sessions
    }

    suspend fun getMessagesForSession(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<ChatMessage>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT text, is_user, status, is_context_eligible FROM ${ScypheonDbHelper.TABLE_MESSAGES} WHERE session_id = ? ORDER BY timestamp ASC", arrayOf(sessionId)).use { cursor ->
            while (cursor.moveToNext()) {
                val encryptedText = cursor.getString(0)
                val decryptedText = enclave.decryptData(encryptedText)
                messages.add(ChatMessage(
                    decryptedText, 
                    cursor.getInt(1) == 1,
                    cursor.getInt(2),
                    cursor.getInt(3) == 1
                ))
            }
        }
        messages
    }

    /**
     * Solaris 4.5: Hybrid Time-Aware Semantic Search.
     * Combines BM25, Cosine Similarity, and Recency Weighting.
     * Optimized for Multipurpose Agentic AI (Palugada).
     */
    suspend fun searchSimilarMemories(query: String, currentSessionId: String? = null, limit: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val queryEmbedding = vectorManager.embedText(query) ?: return@withContext emptyList()
        val db = dbHelper.readableDatabase

        val vectorResults = mutableListOf<Pair<Long, Double>>()
        val textMap = mutableMapOf<Long, String>()
        val vectorRanks = mutableMapOf<Long, Int>()
        
        val currentTime = System.currentTimeMillis()

        // 1. Vector Search with Session & Time Weighting
        // Limit to 1000 latest records for performance hardening on edge devices
        val sql = """
            SELECT id, text, embedding, timestamp, session_id 
            FROM ${ScypheonDbHelper.TABLE_MESSAGES} 
            WHERE embedding IS NOT NULL AND status = ${ScypheonDbHelper.STATUS_SUCCESS}
            ORDER BY timestamp DESC LIMIT 1000
        """.trimIndent()

        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val encryptedText = cursor.getString(1)
                val blob = cursor.getBlob(2)
                val timestamp = cursor.getLong(3)
                val sessionId = cursor.getString(4)

                if (blob == null || blob.isEmpty()) continue

                val floatArray = try {
                    val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
                    FloatArray(blob.size / 4) { buffer.float }
                } catch (e: Exception) { continue }

                if (floatArray.size == queryEmbedding.size) {
                    var similarity = vectorManager.calculateCosineSimilarity(queryEmbedding, floatArray)
                    
                    // Solaris Hardening: Recency Boost (Linear Decay over 7 days)
                    val ageDays = (currentTime - timestamp) / (1000.0 * 60 * 60 * 24)
                    val timeWeight = (1.0 - (ageDays / 7.0)).coerceIn(0.7, 1.0)
                    
                    // Solaris Hardening: Session Boost (Prioritize current context)
                    val sessionWeight = if (sessionId == currentSessionId) 1.2 else 1.0
                    
                    val finalScore = similarity * timeWeight * sessionWeight
                    
                    if (finalScore > 0.60) { // Slightly lower threshold for hybrid catch
                        val decryptedText = enclave.decryptData(encryptedText)
                        if (!decryptedText.startsWith("Error:")) {
                            vectorResults.add(Pair(id, finalScore))
                            textMap[id] = decryptedText
                        }
                    }
                }
            }
        }

        vectorResults.sortByDescending { it.second }
        vectorResults.forEachIndexed { index, pair -> vectorRanks[pair.first] = index + 1 }

        // 2. Keyword Search (BM25 / FTS) - Global Catch
        val keywordRanks = mutableMapOf<Long, Int>()
        val ftsQuery = query.split(Regex("\\s+")).filter { it.length > 2 }.joinToString(" ") { "$it*" }

        if (ftsQuery.isNotBlank()) {
            val ftsSql = "SELECT rowid, text FROM ${ScypheonDbHelper.TABLE_MESSAGES}_fts WHERE text MATCH ? LIMIT 10"
            db.rawQuery(ftsSql, arrayOf(ftsQuery)).use { cursor ->
                var rank = 1
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    keywordRanks[id] = rank++
                    if (!textMap.containsKey(id)) {
                        val encryptedText = cursor.getString(1)
                        val decryptedText = enclave.decryptData(encryptedText)
                        if (!decryptedText.startsWith("Error:")) {
                            textMap[id] = decryptedText
                        }
                    }
                }
            }
        }

        // 3. Reciprocal Rank Fusion (RRF) with Multipurpose Tuning
        val rrfConstant = 60
        val rrfScores = mutableListOf<Pair<String, Double>>()

        for ((id, text) in textMap) {
            val vRank = vectorRanks[id]
            val kRank = keywordRanks[id]

            var rrfScore = 0.0
            if (vRank != null) rrfScore += 1.0 / (rrfConstant + vRank)
            if (kRank != null) rrfScore += 1.0 / (rrfConstant + kRank)

            rrfScores.add(Pair(text, rrfScore))
        }

        rrfScores.sortByDescending { it.second }
        Timber.i("🛰️ Solaris Hybrid Search: Query='$query', Matches=${rrfScores.size}")
        return@withContext rrfScores.take(limit).map { it.first }
    }

    private fun rebuildFts(db: SQLiteDatabase) {
        try {
            Timber.w("[v1.0.4-SAR] DROPPING corrupt FTS index and triggers for recovery.")
            db.execSQL("DROP TABLE IF EXISTS ${ScypheonDbHelper.TABLE_MESSAGES}_fts")
            db.execSQL("DROP TRIGGER IF EXISTS messages_ai")
            db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
            db.execSQL("DROP TRIGGER IF EXISTS messages_au")
            
            // Re-create FTS table
            db.execSQL("""
                CREATE VIRTUAL TABLE ${ScypheonDbHelper.TABLE_MESSAGES}_fts USING fts4(
                    content="${ScypheonDbHelper.TABLE_MESSAGES}",
                    text
                )
            """.trimIndent())
            
            // Re-create Triggers
            db.execSQL("""
                CREATE TRIGGER messages_ai AFTER INSERT ON ${ScypheonDbHelper.TABLE_MESSAGES}
                BEGIN
                    INSERT INTO ${ScypheonDbHelper.TABLE_MESSAGES}_fts(rowid, text) VALUES (new.id, new.text);
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER messages_ad AFTER DELETE ON ${ScypheonDbHelper.TABLE_MESSAGES}
                BEGIN
                    INSERT INTO ${ScypheonDbHelper.TABLE_MESSAGES}_fts(${ScypheonDbHelper.TABLE_MESSAGES}_fts, rowid, text) VALUES ('delete', old.id, old.text);
                END;
            """)

            db.execSQL("""
                CREATE TRIGGER messages_au AFTER UPDATE ON ${ScypheonDbHelper.TABLE_MESSAGES}
                BEGIN
                    UPDATE ${ScypheonDbHelper.TABLE_MESSAGES}_fts SET text = new.text WHERE rowid = old.id;
                END;
            """)
            
            db.execSQL("INSERT INTO ${ScypheonDbHelper.TABLE_MESSAGES}_fts(rowid, text) SELECT id, text FROM ${ScypheonDbHelper.TABLE_MESSAGES}")
            
            Timber.i("[v1.2.0-SAR] FTS Index and Triggers Rebuild successful.")
        } catch (e: Exception) {
            Timber.e(e, "[v1.2.0-SAR] CRITICAL: FTS Rebuild failed.")
        }
    }
}
