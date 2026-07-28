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
import com.scypheon.sdk.core.gateway.filterWithThoughtSuppression
import com.scypheon.sdk.core.security.ZeroKnowledgeEnclave
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.resilience.CircuitBreakerOpenException
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val status: Int = ScypheonDbHelper.STATUS_SUCCESS,
    val isContextEligible: Boolean = true
)

data class Session(val id: String, val title: String, val timestamp: Long, val isArchived: Boolean = false)

@Singleton
class DualMemoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vectorManager: IVectorEngine,
    private val graphManager: GraphMemoryManager,
    private val circuitBreaker: ResilienceCircuitBreaker
) {
    // Solaris Hardening: Mutex to prevent race conditions during DB writes
    private val dbLock = Mutex()
    private val extractionLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getContext(): Context = context

    suspend fun addFact(subject: String, predicate: String, obj: String, confidence: Float = 1.0f) {
        val factString = "[$subject] $predicate $obj"
        
        // 🛡EE[v5.0] SHA-256 Deduplication Gate
        val entry = MemoryEntry(
            content = factString,
            tier = MemoryTier.SEMANTIC,
            importance = confidence
        )
        val hash = entry.calculateContentHash()
        
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("content_hash", hash)
                put("content", entry.content)
                put("tier", entry.tier.name)
                put("timestamp", entry.timestamp)
                put("importance", entry.importance)
            }
            
            try {
                // INSERT OR IGNORE for deduplication
                val id = db.insertWithOnConflict(
                    ScypheonDbHelper.TABLE_MEMORY_ENTRIES, 
                    null, 
                    values, 
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                
                if (id == -1L) {
                    Timber.d("🔄 [DEDUPLICATION] Fact already exists: $factString")
                } else {
                    Timber.i("🧠 [MEMORY_TIER] SEMANTIC Fact anchored: $factString")
                    graphManager.addFact(subject, predicate, obj, confidence)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to anchor fact to Enterprise Memory")
            }
        }
    }

    fun saveFact(subject: String, relation: String, obj: String) {
        scope.launch {
            addFact(subject, relation, obj)
        }
    }

    suspend fun getMemoryContext(query: String): String {
        return when (val factsResult = graphManager.querySubject(query)) {
            is QueryResult.Success -> {
                val facts = factsResult.data
                if (facts.isEmpty()) "No direct facts found." else facts.joinToString("; ")
            }
            is QueryResult.Degraded -> "[SYSTEM_WARNING: Graph database is offline. Cannot retrieve context.]"
            is QueryResult.Error -> "[SYSTEM_WARNING: Error accessing graph database. Cannot retrieve context.]"
        }
    }

    private val dbHelper = ScypheonDbHelper(context)
    private val enclave = ZeroKnowledgeEnclave(context)

    // --- Transactional ACID Writes ---

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
                    
                    db.beginTransaction()
                    try {
                        db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
                        for (id in cappedIds) {
                            db.delete(ScypheonDbHelper.TABLE_MESSAGES, "id = ?", arrayOf(id.toString()))
                        }
                        db.setTransactionSuccessful()
                        purgedCount = cappedIds.size
                    } finally {
                        if (db.inTransaction()) db.endTransaction()
                    }
                    
                    rebuildFts(db)
                    Timber.i("🛡EE[PHOENIX] Startup purge: Deleted $purgedCount corrupted messages and healed FTS index.")
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
                db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
                db.execSQL("DROP TRIGGER IF EXISTS messages_ai")
                db.execSQL("DROP TRIGGER IF EXISTS messages_au")

                db.delete(ScypheonDbHelper.TABLE_SESSIONS, null, null)
                db.delete(ScypheonDbHelper.TABLE_MESSAGES, null, null)
                
                db.setTransactionSuccessful()
            } catch (e: Exception) {
                Timber.e(e, "Nuclear reset failed during deletion phase")
                throw e
            } finally {
                if (db.inTransaction()) db.endTransaction()
            }

            rebuildFts(db)
            Timber.i("🛡EE[PHOENIX] Nuclear reset successful. All memories purged and index healed.")
        }
    }

    suspend fun performTtlSweep(thirtyDaysAgo: Long) = withContext(Dispatchers.IO) {
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            try {
                db.beginTransaction()
                db.delete(ScypheonDbHelper.TABLE_SESSIONS, "timestamp < ?", arrayOf(thirtyDaysAgo.toString()))
                db.delete(ScypheonDbHelper.TABLE_MESSAGES, "timestamp < ?", arrayOf(thirtyDaysAgo.toString()))
                
                // --- Volatile Medical Context Auto-Expunge ---
                // Scypheon Private Hardening: Any memory entry (Graph/Semantic) older than 30 minutes containing medical keywords is wiped
                val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)
                db.delete(ScypheonDbHelper.TABLE_MEMORY_ENTRIES, 
                          "timestamp < ? AND (LOWER(content) LIKE '%allergy%' OR LOWER(content) LIKE '%prescription%' OR LOWER(content) LIKE '%weight%' OR LOWER(content) LIKE '%medical%')", 
                          arrayOf(thirtyMinutesAgo.toString()))
                
                db.setTransactionSuccessful()
                Timber.i("[Memory] TTL Sweep: Purged old data and Volatile Medical Contexts.")
            } catch (e: Exception) {
                Timber.e(e, "[Memory] TTL Sweep failed")
            } finally {
                if (db.inTransaction()) db.endTransaction()
            }
        }
    }

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

            rebuildFts(db)
        }
    }

    suspend fun saveSummaryToLongTerm(sessionId: String, summary: String) = withContext(Dispatchers.IO) {
        dbLock.withLock {
            try {
                val embedding = vectorManager.embedText(summary) ?: return@withLock
                val db = dbHelper.writableDatabase
                val encryptedSummary = enclave.encryptData(summary)
                
                val values = ContentValues().apply {
                    put("session_id", sessionId)
                    put("text", encryptedSummary)
                    put("is_user", 0)
                    put("timestamp", System.currentTimeMillis())
                    put("is_context_eligible", 0) 
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

    suspend fun wipeSessionMemory(sessionId: String) = withContext(Dispatchers.IO) {
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            try {
                db.beginTransaction()
                db.delete(ScypheonDbHelper.TABLE_MESSAGES, "session_id = ?", arrayOf(sessionId))
                db.delete(ScypheonDbHelper.TABLE_SESSIONS, "id = ?", arrayOf(sessionId))
                db.setTransactionSuccessful()
                Timber.i("[Memory] Wiped session: $sessionId")
            } catch (e: Exception) {
                Timber.e(e, "[Memory] Session wipe failed: $sessionId")
                throw e
            } finally {
                if (db.inTransaction()) db.endTransaction()
            }
        }
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", title)
        }
        db.update(ScypheonDbHelper.TABLE_SESSIONS, values, "id = ?", arrayOf(sessionId))
    }

    suspend fun archiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_archived", 1)
        }
        db.update(ScypheonDbHelper.TABLE_SESSIONS, values, "id = ?", arrayOf(sessionId))
    }

    suspend fun unarchiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_archived", 0)
        }
        db.update(ScypheonDbHelper.TABLE_SESSIONS, values, "id = ?", arrayOf(sessionId))
    }

    suspend fun expireAwaitingApprovalTasks(fifteenMinsAgo: Long) = withContext(Dispatchers.IO) {
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            try {
                db.beginTransaction()
                db.delete(ScypheonDbHelper.TABLE_MESSAGES, 
                    "text LIKE '[AWAITING_APPROVAL]%' AND timestamp < ?", 
                    arrayOf(fifteenMinsAgo.toString()))
                
                db.setTransactionSuccessful()
                Timber.i("[Memory] Expired zombie AWAITING_APPROVAL tasks older than $fifteenMinsAgo")
            } catch (e: Exception) {
                Timber.e(e, "[Memory] Failed to expire zombie tasks")
            } finally {
                if (db.inTransaction()) db.endTransaction()
            }
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

        // CRITICAL: Hold lock ONLY for the synchronous DB write
        dbLock.withLock {
            val db = dbHelper.writableDatabase
            try {
                db.beginTransaction()
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
                if (db.inTransaction()) db.endTransaction()
            }
        }
        // Lock RELEASED — async enrichment runs without blocking other DB writers

        if (finalMessageId != -1L) {
            scope.launch {
                // Async embedding: CPU-bound work runs without lock
                try {
                    val embedding = vectorManager.embedText(text)
                    if (embedding != null && embedding.isNotEmpty()) {
                        val buffer = ByteBuffer.allocate(embedding.size * 4).apply {
                            order(ByteOrder.LITTLE_ENDIAN)
                            embedding.forEach { putFloat(it) }
                        }
                        // Re-acquire lock only for the DB update
                        dbLock.withLock {
                            val dbAsync = dbHelper.writableDatabase
                            val updateValues = ContentValues().apply {
                                put("embedding", buffer.array())
                            }
                            dbAsync.update(ScypheonDbHelper.TABLE_MESSAGES, updateValues, "id = ?", arrayOf(finalMessageId.toString()))
                        }
                        Timber.d("Async embedding attached for message $finalMessageId")
                    }
                } catch (e: Exception) {
                    Timber.w("Async embedding failed for message $finalMessageId: ${e.message}")
                }

                extractAndAnchorFacts(text, isUser)
            }
        }
        finalMessageId
    }

    private var gateway: NeuralGateway? = null
    
    fun setGateway(gw: NeuralGateway) { this.gateway = gw }

    private suspend fun extractAndAnchorFacts(text: String, isUser: Boolean) {
        if (text.length < 15) return
        if (text.startsWith("[") || text.startsWith("⚠") || text.startsWith("Error:")) return

        if (isUser) {
            kotlinx.coroutines.delay(12000L) 
        } else {
            kotlinx.coroutines.delay(4000L)  
        }

        val gw = gateway ?: run {
            Timber.w("[MEMORY] Gateway not set  Efalling back to heuristic extraction")
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

            extractionLock.withLock {
                kotlinx.coroutines.withTimeout(15000) {
                    val sb = StringBuilder()
                    gw.routeRequest(extractionPrompt, enableThinking = false)
                        .filterWithThoughtSuppression()
                        .collect { chunk: String -> sb.append(chunk) }

                    parseAndStoreFacts(sb.toString().trim())
                }
            }

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.w("[MEMORY] LLM extraction timed out  Efalling back to heuristic")
            extractFactsHeuristic(text, isUser)
        } catch (e: Exception) {
            Timber.e(e, "[MEMORY] LLM fact extraction failed  Efalling back to heuristic")
            extractFactsHeuristic(text, isUser)
        }
    }

    private suspend fun parseAndStoreFacts(response: String) {
        if (response.equals("NONE", ignoreCase = true) || response.isBlank()) return
        
        try {
            var cleaned = response.trim()
            if (cleaned.startsWith("```")) {
                val lines = cleaned.split("\n")
                val middleLines = lines.filterIndexed { index, _ -> 
                    index > 0 && index < lines.size - 1 
                }
                cleaned = middleLines.joinToString("\n").trim()
            } else if (cleaned.contains("```")) {
                cleaned = cleaned.replace("```json", "").replace("```", "").trim()
            }
            
            val arrayStart = cleaned.indexOf('[')
            val arrayEnd = cleaned.lastIndexOf(']')
            
            val objStart = cleaned.indexOf('{')
            val objEnd = cleaned.lastIndexOf('}')
            
            val jsonStr = when {
                arrayStart != -1 && arrayEnd != -1 && arrayStart < arrayEnd -> {
                    cleaned.substring(arrayStart, arrayEnd + 1)
                }
                objStart != -1 && objEnd != -1 && objStart < objEnd -> {
                    "[" + cleaned.substring(objStart, objEnd + 1) + "]"
                }
                else -> return
            }
            
            val jsonArray = org.json.JSONArray(jsonStr)
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val subject = obj.optString("s", "").trim()
                val predicate = obj.optString("p", "").trim()
                val objectVal = obj.optString("o", "").trim()
                
                if (subject.isNotEmpty() && predicate.isNotEmpty() && objectVal.isNotEmpty()) {
                    val conflictResult = graphManager.queryConflictingFact(subject, predicate, objectVal)
                    when (conflictResult) {
                        is QueryResult.Success -> {
                            val conflict = conflictResult.data
                            if (conflict != null) {
                                if (isSingleValuedPredicate(predicate)) {
                                    graphManager.resolveConflictingFact(subject, predicate, conflict)
                                    Timber.i("🔄 [MEMORY] Resolved single-valued conflict: [$subject] [$predicate] $conflict -> $objectVal")
                                } else {
                                    Timber.i("🔄 [MEMORY] Updating fact: [$subject] [$predicate] $conflict -> $objectVal")
                                }
                            }
                            graphManager.addFact(subject, predicate, objectVal)
                            Timber.i("🧠 [MEMORY] LLM extracted: [$subject] -> [$predicate] -> [$objectVal]")
                        }
                        is QueryResult.Degraded, is QueryResult.Error -> {
                            Timber.w("Skipping fact resolution/addition due to GraphRAG degradation.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[MEMORY] Failed to parse LLM extraction response: ${response.take(200)}")
        }
    }

    private fun isSingleValuedPredicate(predicate: String): Boolean {
        val p = predicate.lowercase().trim()
        return p == "lives in" || p == "tinggal di" || p == "domisili" ||
               p == "works as" || p == "bekerja sebagai" ||
               p == "name is" || p == "nama nya" || p == "nama" ||
               p == "birthday is" || p == "tanggal lahir" || p == "born on" ||
               p == "is" || p == "adalah"
    }

    private suspend fun extractFactsHeuristic(text: String, isUser: Boolean) {
        try {
            val lower = text.lowercase()
            val idPatterns = listOf(
                Regex("(?:aku|saya|gue|gw)\\s+(?:suka|senang|seneng|doyan)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "likes",
                Regex("(?:aku|saya|gue|gw)\\s+(?:benci|ga suka|gak suka|tidak suka|nggak suka)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "dislikes",
                Regex("(?:aku|saya|gue|gw)\\s+(?:cinta|sayang|naksir)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "loves",
                Regex("(?:aku|saya|gue|gw)\\s+(?:alergi|allergic)\\s+(?:sama|terhadap|dengan)?\\s*(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "is allergic to",
                Regex("(?:nama\\s+(?:aku|saya|gue|gw)|(?:aku|saya|gue|gw)\\s+nama(?:nya)?)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "name is",
                Regex("(?:aku|saya|gue|gw)\\s+(?:tinggal|domisili)\\s+(?:di)?\\s*(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "lives in",
                Regex("(?:aku|saya|gue|gw)\\s+(?:kerja|bekerja)\\s+(?:sebagai|jadi)?\\s*(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "works as",
                Regex("(?:aku|saya|gue|gw)\\s+(?:takut|fobia)\\s+(?:sama|dengan|terhadap)?\\s*(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "fears",
                Regex("(?:aku|saya|gue|gw)\\s+(?:punya|memiliki)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "has"
            )
            
            val enPatterns = listOf(
                Regex("(?:i|my)\\s+(?:like|love|enjoy|adore)s?\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "likes",
                Regex("(?:i|my)\\s+(?:hate|dislike|can't stand)s?\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "dislikes",
                Regex("(?:i'm|i am)\\s+(?:allergic to)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "is allergic to",
                Regex("my\\s+name\\s+is\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "name is",
                Regex("(?:i'm|i am)\\s+(?:afraid of|scared of)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "fears",
                Regex("(?:i)\\s+(?:live|stay)\\s+(?:in|at)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "lives in",
                Regex("(?:i)\\s+(?:work|working)\\s+(?:as|at)\\s+(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "works as",
                Regex("(?:i)\\s+(?:have|own)\\s+(?:a|an)?\\s*(.+?)(?:\\.|,|!|\\?|;|:|$|\\n)", RegexOption.IGNORE_CASE) to "has"
            )
            
            val patterns = if (isUser) idPatterns + enPatterns else emptyList()
            
            for ((regex, predicate) in patterns) {
                regex.find(lower)?.let { match ->
                    val objectVal = match.groupValues[1].trim()
                    if (objectVal.length in 2..50) { 
                        graphManager.addFact("user", predicate, objectVal)
                        Timber.i("🧠 [MEMORY/HEURISTIC] Extracted: [user] -> [$predicate] -> [$objectVal]")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[MEMORY] Heuristic fact anchoring failed")
        }
    }

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

    suspend fun getUserAllergies(): String = withContext(Dispatchers.IO) {
        var profileAllergies = "None recorded"
        try {
            circuitBreaker.execute("profile_memory_read") {
                val db = dbHelper.readableDatabase
                db.rawQuery("SELECT value FROM ${ScypheonDbHelper.TABLE_PROFILE} WHERE key = 'allergies'", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val encryptedVal = cursor.getString(0)
                        profileAllergies = enclave.decryptData(encryptedVal)
                    }
                }
            }
        } catch (e: CircuitBreakerOpenException) {
            Timber.w("Profile DB circuit open. Falling back to graph only.")
        } catch (e: Exception) {
            Timber.w(e, "Profile DB read failed. Falling back to graph only.")
        }

        when (val graphResult = graphManager.getAllergies()) {
            is QueryResult.Success -> {
                if (graphResult.data.isEmpty()) profileAllergies
                else "$profileAllergies. Graph: ${graphResult.data}"
            }
            is QueryResult.Degraded -> {
                SystemAlert.DegradedAllergy.toPromptString()
            }
            is QueryResult.Error -> {
                Timber.e(graphResult.cause, "Graph query error")
                SystemAlert.DegradedAllergy.toPromptString()
            }
        }
    }

    data class ExportableMemory(val id: String, val summary: String)

    suspend fun getMemoriesByCategory(category: String): List<ExportableMemory> = withContext(Dispatchers.IO) {
        val facts = mutableListOf<Triple<String, String, String>>()
        when (category.lowercase()) {
            "medical" -> {
                (graphManager.queryByPredicate("is_allergic_to") as? QueryResult.Success)?.data?.let { facts.addAll(it) }
                (graphManager.queryByPredicate("takes_medicine") as? QueryResult.Success)?.data?.let { facts.addAll(it) }
                (graphManager.queryByPredicate("mengonsumsi") as? QueryResult.Success)?.data?.let { facts.addAll(it) }
            }
            "scam" -> {
                (graphManager.queryByPredicate("is_scam") as? QueryResult.Success)?.data?.let { facts.addAll(it) }
                (graphManager.queryByPredicate("reported_fraud") as? QueryResult.Success)?.data?.let { facts.addAll(it) }
                (graphManager.queryByPredicate("penipuan") as? QueryResult.Success)?.data?.let { facts.addAll(it) }
            }
        }
        
        facts.mapIndexed { index, fact ->
            ExportableMemory(
                id = "${category.uppercase()}_FACT_$index",
                summary = "[${fact.first}] ${fact.second} ${fact.third}"
            )
        }
    }

    suspend fun getCurrentPrescriptions(): List<String> = withContext(Dispatchers.IO) {
        val r1 = graphManager.queryByPredicate("takes_medicine")
        val r2 = graphManager.queryByPredicate("mengonsumsi")
        
        val facts = mutableListOf<Triple<String, String, String>>()
        if (r1 is QueryResult.Success) facts.addAll(r1.data)
        if (r2 is QueryResult.Success) facts.addAll(r2.data)
        
        facts.map { it.third }.distinct()
    }

    suspend fun querySubject(entity: String): List<String> {
        val result = graphManager.querySubject(entity)
        return if (result is QueryResult.Success) result.data else emptyList()
    }

    suspend fun getRawKnowledgeGraph(): List<Triple<String, String, String>> {
        val result = graphManager.getFullGraph()
        return if (result is QueryResult.Success) result.data else emptyList()
    }

    suspend fun getAllSessions(): List<Session> = withContext(Dispatchers.IO) {
        val sessions = mutableListOf<Session>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT id, title, timestamp, is_archived FROM ${ScypheonDbHelper.TABLE_SESSIONS} ORDER BY timestamp DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                sessions.add(Session(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getLong(2),
                    cursor.getInt(3) == 1
                ))
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

    suspend fun searchSimilarMemories(query: String, currentSessionId: String? = null, limit: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val queryEmbedding = vectorManager.embedText(query) ?: return@withContext emptyList()
        val db = dbHelper.readableDatabase

        val vectorResults = mutableListOf<Pair<Long, Double>>()
        val textMap = mutableMapOf<Long, String>()
        val vectorRanks = mutableMapOf<Long, Int>()
        
        val currentTime = System.currentTimeMillis()

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
                    
                    val ageDays = (currentTime - timestamp) / (1000.0 * 60 * 60 * 24)
                    val timeWeight = (1.0 - (ageDays / 7.0)).coerceIn(0.7, 1.0)
                    
                    val sessionWeight = if (sessionId == currentSessionId) 1.2 else 1.0
                    
                    val finalScore = similarity * timeWeight * sessionWeight
                    
                    if (finalScore > 0.60) { 
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
        Timber.i("🛰EESolaris Hybrid Search: Query='$query', Matches=${rrfScores.size}")
        return@withContext rrfScores.take(limit).map { it.first }
    }

    private fun rebuildFts(db: SQLiteDatabase) {
        Timber.i("[v6.0] rebuildFts called, but FTS is deprecated. Skipping.")
    }
}
