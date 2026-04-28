package com.scypheon.sdk.core.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.os.Build
import com.scypheon.sdk.core.gateway.NeuralGateway
import com.scypheon.sdk.core.security.ZeroKnowledgeEnclave

/**
 * Enterprise Dual Memory Manager.
 * Handles Short-Term context (SQLite) and Long-Term semantic embeddings (IVectorEngine).
 */
class DualMemoryManager(
    private val context: Context,
    private val vectorManager: IVectorEngine,
    private val graphManager: GraphMemoryManager
) {
    fun saveFact(subject: String, relation: String, obj: String) {
        graphManager.addFact(subject, relation, obj)
    }

    fun querySubject(subject: String): List<String> {
        return graphManager.querySubject(subject)
    }

    private val dbHelper = ScypheonDbHelper(context)
    private val enclave = ZeroKnowledgeEnclave(context)

    data class ChatMessage(
        val text: String, 
        val isUser: Boolean, 
        val status: Int = ScypheonDbHelper.STATUS_SUCCESS,
        val isContextEligible: Boolean = true
    )
    data class Session(val id: String, val title: String, val timestamp: Long)

    // --- Transactional ACID Writes ---

    /**
     * One-time startup cleanup: Delete all persisted engine error messages from the DB.
     * Before the MEMORY GUARD fix, error strings like "Error: AI engine disconnected..."
     * were saved as legitimate assistant responses. These must be purged to prevent
     * permanent prompt contamination causing the infinite generation loop.
     */
    fun purgeEngineErrorMessages(): Int {
        val db = dbHelper.writableDatabase
        var purgedCount = 0
        db.beginTransaction()
        try {
            val idsToDelete = mutableListOf<Long>()
            db.rawQuery(
                "SELECT id, text FROM ${ScypheonDbHelper.TABLE_MESSAGES} WHERE is_user = 0",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val rawText = cursor.getString(1)
                    val decrypted = try { enclave.decryptData(rawText) } catch (e: Exception) { "Error: corrupt" }
                    if (decrypted.startsWith("Error:")) {
                        idsToDelete.add(id)
                    }
                }
            }
            if (idsToDelete.isNotEmpty()) {
                // Architect Directive: High-performance batch deletion. 
                // Prevents N+1 overhead and WAL lock timeouts.
                val cappedIds = idsToDelete.take(500).joinToString(",")
                db.execSQL("DELETE FROM ${ScypheonDbHelper.TABLE_MESSAGES} WHERE id IN ($cappedIds)")
                
                db.setTransactionSuccessful()
                Timber.i("[v1.1.0-SAR] Startup purge: Batch-deleted ${idsToDelete.take(500).size} corrupted messages.")
            } else {
                db.setTransactionSuccessful()
            }
        } catch (e: Exception) {
            Timber.e(e, "[v1.0.4-SAR] Startup error purge failed. Database may be in an inconsistent state.")
        } finally {
            if (db.inTransaction()) db.endTransaction()
        }
        return purgedCount
    }

    /**
     * Enterprise Protocol: Database TTL Sweep (AGENTS.md Section 3)
     * Purge historical messages and sessions older than the specified timestamp.
     */
    fun performTtlSweep(thirtyDaysAgo: Long) {
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
    fun replaceMessagesWithSummary(sessionId: String, countToReplace: Int, summaryText: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Get the IDs of the oldest N messages
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
                return
            }

            // v1.0.3-SAR: Individual deletes to prevent FTS trigger corruption logic errors
            for (id in idsToDelete) {
                db.delete(ScypheonDbHelper.TABLE_MESSAGES, "id = ?", arrayOf(id.toString()))
            }

            // Insert the new summary message at the top, secured by the Enclave
            val encryptedSummary = enclave.encryptData(summaryText)
            val values = ContentValues().apply {
                put("session_id", sessionId)
                put("text", encryptedSummary)
                put("is_user", 0) // Summary counts as AI-generated context
                put("timestamp", System.currentTimeMillis() - 1000000L) // Ensure it appears at the beginning of the timeline
            }
            db.insert(ScypheonDbHelper.TABLE_MESSAGES, null, values)

            db.setTransactionSuccessful()
            Timber.i("[v1.0.3-SAR] Replaced ${idsToDelete.size} raw messages with 1 summary block.")
        } catch (e: Exception) {
            Timber.e(e, "[v1.0.3-SAR] Failed to replace messages with summary. Triggering FTS Rebuild.")
            rebuildFts(db)
        } finally {
            if (db.inTransaction()) db.endTransaction()
        }
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

    fun updateSessionTitle(sessionId: String, newTitle: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", newTitle)
        }
        db.update(ScypheonDbHelper.TABLE_SESSIONS, values, "id = ?", arrayOf(sessionId))
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

    fun createSession(sessionId: String, title: String) {
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

    // A standalone supervisor scope for DB operations to ensure they aren't killed prematurely
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun saveMessage(
        sessionId: String, 
        text: String, 
        isUser: Boolean, 
        status: Int = ScypheonDbHelper.STATUS_SUCCESS,
        isContextEligible: Boolean = true
    ): Long {
        val db = dbHelper.writableDatabase
        var messageId = -1L

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
            messageId = db.insert(ScypheonDbHelper.TABLE_MESSAGES, null, values)
            db.setTransactionSuccessful()
            Timber.i("Successfully saved message record (ID=$messageId, Status=$status)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save message record")
        } finally {
            db.endTransaction()
        }

        // Offload vector generation and fact anchoring as background tasks
        if (messageId != -1L) {
            scope.launch {
                // 1. Semantic Embedding
                try {
                    val embedding = vectorManager.embedText("U: $isUser | MSG: $text")
                    if (embedding != null && embedding.isNotEmpty()) {
                        val buffer = ByteBuffer.allocate(embedding.size * 4).apply {
                            order(ByteOrder.nativeOrder())
                            embedding.forEach { putFloat(it) }
                        }
                        val dbAsync = dbHelper.writableDatabase
                        val values = ContentValues().apply {
                            put("embedding", buffer.array())
                        }
                        dbAsync.update(ScypheonDbHelper.TABLE_MESSAGES, values, "id = ?", arrayOf(messageId.toString()))
                    }
                } catch (e: Exception) {
                    Timber.w("Async embedding failed for message $messageId: ${e.message}")
                }

                // 2. Fact Anchoring (Competitive Edge Memory Resilience)
                if (!isUser) {
                    extractAndAnchorFacts(text)
                }
            }
        }
        
        return messageId
    }

    /**
     * Semantic Fact Anchoring: Extracts knowledge triplets from AI turns.
     * Pattern: [KNOWLEDGE: Subject, Relation, Object]
     * Includes Conflict Resolution logic to ensure memory integrity.
     */
    private fun extractAndAnchorFacts(text: String) {
        val pattern = Regex("\\[KNOWLEDGE:\\s*([^,]+),\\s*([^,]+),\\s*([^\\]]+)\\]")
        val matches = pattern.findAll(text)
        
        matches.forEach { match ->
            val subject = match.groupValues[1].trim()
            val relation = match.groupValues[2].trim()
            val obj = match.groupValues[3].trim()
            
            // Conflict Detection
            val existingConflict = graphManager.queryConflictingFact(subject, relation, obj)
            
            if (existingConflict != null) {
                Timber.w("🚨 [MEMORY CONFLICT] Contradiction detected for ($subject, $relation).")
                Timber.w("   Existing: $existingConflict")
                Timber.w("   New: $obj")
                Timber.w("   Action: Anchoring with [CONFLICTED] flag (Placeholder for verification flow).")
                
                // For now, we update with a lower confidence or special predicate prefix
                graphManager.addFact(subject, "conflicts_with_$relation", obj, 0.5f)
            } else {
                Timber.i("🧠 [MEMORY ANCHOR] Anchoring Fact: ($subject) --[$relation]--> ($obj)")
                graphManager.addFact(subject, relation, obj)
            }
        }
    }

    /**
     * Updates the status of a specific message (e.g. from QUEUED to SUCCESS or FAILED).
     */
    fun updateMessageStatus(id: Long, status: Int) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", status)
        }
        db.update(ScypheonDbHelper.TABLE_MESSAGES, values, "id = ?", arrayOf(id.toString()))
    }

    fun updateUserAllergies(allergies: String) {
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
    fun getUserAllergies(): String {
        val db = dbHelper.readableDatabase
        var profileAllergies = "None recorded"
        db.rawQuery("SELECT value FROM ${ScypheonDbHelper.TABLE_PROFILE} WHERE key = 'allergies'", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val encryptedVal = cursor.getString(0)
                profileAllergies = enclave.decryptData(encryptedVal)
            }
        }

        val graphAllergies = graphManager.getAllergies()
        return if (graphAllergies.contains("No known") || graphAllergies.isEmpty()) {
            profileAllergies
        } else {
            "$profileAllergies. Graph Deductions: $graphAllergies"
        }
    }


    /**
     * Retrieve all deeply connected Graph facts for a given entity.
     */
    fun getEntityFacts(entity: String): List<String> {
        return graphManager.querySubject(entity)
    }

    /**
     * Extract the raw knowledge graph.
     */
    fun getRawKnowledgeGraph(): List<Triple<String, String, String>> {
        return graphManager.getFullGraph()
    }

    fun getAllSessions(): List<Session> {
        val sessions = mutableListOf<Session>()
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT id, title, timestamp FROM ${ScypheonDbHelper.TABLE_SESSIONS} ORDER BY timestamp DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                sessions.add(Session(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
            }
        }
        return sessions
    }

    fun getMessagesForSession(sessionId: String): List<ChatMessage> {
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
        return messages
    }

    /**
     * Enterprise Edge Max: Hybrid Search with Reciprocal Rank Fusion (RRF).
     * Combines exact BM25 keyword matching (via SQLite FTS) with Semantic Vector Cosine Similarity.
     */
    suspend fun searchSimilarMemories(query: String, limit: Int = 3): List<String> {
        val queryEmbedding = vectorManager.embedText(query) ?: return emptyList()
        val db = dbHelper.readableDatabase

        // 1. Vector Search (Semantic)
        val vectorResults = mutableListOf<Pair<Long, Float>>()
        val textMap = mutableMapOf<Long, String>()

        db.rawQuery("SELECT id, text, embedding FROM ${ScypheonDbHelper.TABLE_MESSAGES} WHERE embedding IS NOT NULL", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val blob = cursor.getBlob(2)
                if (blob == null || blob.isEmpty()) continue

                val floatArray = try {
                    val buffer = ByteBuffer.wrap(blob)
                    buffer.order(ByteOrder.nativeOrder())
                    val arr = FloatArray(blob.size / 4)
                    for (i in arr.indices) arr[i] = buffer.float
                    arr
                } catch (e: Exception) {
                    Timber.e(" [SAR] Corrupt embedding blob for ID=$id")
                    continue
                }

                if (floatArray.size != queryEmbedding.size) {
                    Timber.w(" [SAR] Dimension mismatch for ID=$id (Expected ${queryEmbedding.size}, got ${floatArray.size})")
                    continue
                }

                val similarity = vectorManager.calculateCosineSimilarity(queryEmbedding, floatArray)
                if (similarity > 0.65f) {
                    val encryptedText = cursor.getString(1)
                    val decryptedText = enclave.decryptData(encryptedText)
                    vectorResults.add(Pair(id, similarity))
                    textMap[id] = decryptedText
                }
            }
        }

        // Sort Vector results and assign ranks (1 is best)
        vectorResults.sortByDescending { it.second }
        val vectorRanks = vectorResults.mapIndexed { index, pair -> pair.first to (index + 1) }.toMap()

        // 2. Keyword Search (BM25 / FTS)
        val keywordRanks = mutableMapOf<Long, Int>()
        // Simple token matching for FTS query format (e.g. "word1* word2*")
        val ftsQuery = query.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { "$it*" }

        if (ftsQuery.isNotBlank()) {
            val sql = "SELECT rowid, text FROM ${ScypheonDbHelper.TABLE_MESSAGES}_fts WHERE text MATCH ? LIMIT 10"
            db.rawQuery(sql, arrayOf(ftsQuery)).use { cursor ->
                var rank = 1
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val encryptedText = cursor.getString(1)
                    val decryptedText = enclave.decryptData(encryptedText)

                    keywordRanks[id] = rank++
                    if (!textMap.containsKey(id)) textMap[id] = decryptedText
                }
            }
        }

        // 3. Reciprocal Rank Fusion (RRF)
        val rrfConstant = 60 // Standard constant for RRF
        val rrfScores = mutableListOf<Pair<String, Double>>()

        for ((id, text) in textMap) {
            val vRank = vectorRanks[id]
            val kRank = keywordRanks[id]

            var rrfScore = 0.0
            if (vRank != null) rrfScore += 1.0 / (rrfConstant + vRank)
            if (kRank != null) rrfScore += 1.0 / (rrfConstant + kRank)

            rrfScores.add(Pair(text, rrfScore))
        }

        // Sort by highest RRF score
        rrfScores.sortByDescending { it.second }

        Timber.i("🔍 Hybrid RRF Search completed for query: '$query'. Found ${rrfScores.size} fused matches.")
        return rrfScores.take(limit).map { it.first }
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
                    INSERT INTO ${ScypheonDbHelper.TABLE_MESSAGES}_fts(${ScypheonDbHelper.TABLE_MESSAGES}_fts, rowid, text) VALUES ('delete', old.id, old.text);
                    INSERT INTO ${ScypheonDbHelper.TABLE_MESSAGES}_fts(rowid, text) VALUES (new.id, new.text);
                END;
            """)
            
            db.execSQL("INSERT INTO ${ScypheonDbHelper.TABLE_MESSAGES}_fts(${ScypheonDbHelper.TABLE_MESSAGES}_fts) VALUES('rebuild')")
            Timber.i("[v1.0.4-SAR] FTS Index and Triggers Rebuild successful.")
        } catch (e: Exception) {
            Timber.e(e, "[v1.0.4-SAR] CRITICAL: FTS Rebuild failed.")
        }
    }
}
