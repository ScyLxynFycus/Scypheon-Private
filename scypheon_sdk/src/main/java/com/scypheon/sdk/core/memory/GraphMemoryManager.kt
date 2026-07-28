package com.scypheon.sdk.core.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.scypheon.sdk.core.resilience.ResilienceCircuitBreaker
import com.scypheon.sdk.core.resilience.CircuitBreakerOpenException
import timber.log.Timber
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Edge Max: Local GraphRAG Storage.
 * Stores semantic facts as a Knowledge Graph (Subject -> Predicate -> Object) locally in SQLite.
 * Hardened with Enterprise Circuit Breakers and QueryResult propagation.
 */
@Singleton
class GraphMemoryManager @Inject constructor(
    @ApplicationContext context: Context,
    private val circuitBreaker: ResilienceCircuitBreaker
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "ScypheonKnowledgeGraph.db"
        const val DATABASE_VERSION = 1
        const val TABLE_GRAPH = "knowledge_graph"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.rawQuery("PRAGMA synchronous=NORMAL", null).close()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_GRAPH (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject TEXT NOT NULL,
                predicate TEXT NOT NULL,
                object TEXT NOT NULL,
                confidence REAL DEFAULT 1.0,
                timestamp INTEGER NOT NULL,
                UNIQUE(subject, predicate, object) ON CONFLICT REPLACE
            )
        """.trimIndent()
        db.execSQL(createTableQuery)

        db.execSQL("CREATE INDEX idx_subject ON $TABLE_GRAPH(subject)")
        db.execSQL("CREATE INDEX idx_object ON $TABLE_GRAPH(object)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GRAPH")
        onCreate(db)
    }

    suspend fun queryConflictingFact(subject: String, predicate: String, newObj: String): QueryResult<String?> = withContext(Dispatchers.IO) {
        try {
            val result = circuitBreaker.execute("graph_memory_read") {
                val db = readableDatabase
                db.rawQuery(
                    "SELECT object FROM $TABLE_GRAPH WHERE subject = ? AND predicate = ? AND object != ? COLLATE NOCASE",
                    arrayOf(subject.lowercase(), predicate.lowercase(), newObj.lowercase())
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }
            QueryResult.Success(result)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_read")
        } catch (e: Exception) {
            Timber.e(e, "GraphRAG read failed.")
            QueryResult.Error(e)
        }
    }

    suspend fun addFact(subject: String, predicate: String, obj: String, confidence: Float = 1.0f): QueryResult<Unit> = withContext(Dispatchers.IO) {
        try {
            circuitBreaker.execute("graph_memory_write") {
                val db = writableDatabase
                try {
                    db.beginTransaction()
                    db.execSQL(
                        "INSERT INTO $TABLE_GRAPH (subject, predicate, object, confidence, timestamp) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(subject.lowercase(), predicate.lowercase(), obj.lowercase(), confidence, System.currentTimeMillis())
                    )
                    // Prune within the SAME transaction — no nested beginTransaction
                    pruneOldFactsInline(db)
                    db.setTransactionSuccessful()
                    Timber.i("GraphRAG: Ingested Fact: [$subject] -> [$predicate] -> [$obj]")
                } finally {
                    if (db.inTransaction()) db.endTransaction()
                }
            }
            QueryResult.Success(Unit)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_write")
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert fact into Knowledge Graph")
            QueryResult.Error(e)
        }
    }

    /**
     * Prune excess facts using raw execSQL. Designed to be called from within
     * an existing transaction (e.g., from [addFact]) — does NOT start its own
     * transaction to avoid nested EXCLUSIVE lock deadlock under WAL mode.
     */
    private fun pruneOldFactsInline(db: SQLiteDatabase, maxFacts: Int = 1000) {
        db.execSQL("""
            DELETE FROM $TABLE_GRAPH 
            WHERE rowid < (
                SELECT rowid FROM $TABLE_GRAPH 
                ORDER BY rowid DESC 
                LIMIT 1 OFFSET ?
            )
        """, arrayOf(maxFacts))
    }

    suspend fun pruneOldFacts(maxFacts: Int = 1000): QueryResult<Unit> = withContext(Dispatchers.IO) {
        try {
            circuitBreaker.execute("graph_memory_write") {
                val db = writableDatabase
                try {
                    db.beginTransaction()
                    pruneOldFactsInline(db, maxFacts)
                    db.setTransactionSuccessful()
                    Timber.d("GraphRAG: Pruned old facts, kept max $maxFacts")
                } finally {
                    if (db.inTransaction()) db.endTransaction()
                }
            }
            QueryResult.Success(Unit)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_write")
        } catch (e: Exception) {
            Timber.e(e, "Failed to prune old facts from Knowledge Graph")
            QueryResult.Error(e)
        }
    }

    suspend fun querySubject(subject: String): QueryResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val facts = circuitBreaker.execute("graph_memory_read") {
                val resultList = mutableListOf<String>()
                val db = readableDatabase
                db.rawQuery(
                    "SELECT subject, predicate, object FROM $TABLE_GRAPH WHERE subject = ? COLLATE NOCASE ORDER BY timestamp DESC LIMIT 10",
                    arrayOf(subject)
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        resultList.add("${cursor.getString(0)} ${cursor.getString(1)} ${cursor.getString(2)}")
                    }
                }
                resultList.toList()
            }
            QueryResult.Success(facts)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_read")
        } catch (e: Exception) {
            Timber.e(e, "Failed to query Knowledge Graph")
            QueryResult.Error(e)
        }
    }
    
    suspend fun queryByPredicate(predicate: String): QueryResult<List<Triple<String, String, String>>> = withContext(Dispatchers.IO) {
        try {
            val facts = circuitBreaker.execute("graph_memory_read") {
                val resultList = mutableListOf<Triple<String, String, String>>()
                val db = readableDatabase
                db.rawQuery(
                    "SELECT subject, predicate, object FROM $TABLE_GRAPH WHERE predicate = ? COLLATE NOCASE",
                    arrayOf(predicate)
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        resultList.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                    }
                }
                resultList.toList()
            }
            QueryResult.Success(facts)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_read")
        } catch (e: Exception) {
            Timber.e(e, "Failed to query predicate from Knowledge Graph")
            QueryResult.Error(e)
        }
    }

    suspend fun getFullGraph(): QueryResult<List<Triple<String, String, String>>> = withContext(Dispatchers.IO) {
        try {
            val graph = circuitBreaker.execute("graph_memory_read") {
                val resultList = mutableListOf<Triple<String, String, String>>()
                val db = readableDatabase
                db.rawQuery("SELECT subject, predicate, object FROM $TABLE_GRAPH ORDER BY timestamp DESC LIMIT 50", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        resultList.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                    }
                }
                resultList.toList()
            }
            QueryResult.Success(graph)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_read")
        } catch (e: Exception) {
            Timber.e(e, "Failed to dump Knowledge Graph")
            QueryResult.Error(e)
        }
    }

    suspend fun getAllergies(): QueryResult<String> = withContext(Dispatchers.IO) {
        try {
            val result = circuitBreaker.execute("graph_memory_read") {
                val allergies = mutableListOf<String>()
                val db = readableDatabase
                db.rawQuery(
                    "SELECT subject, object FROM $TABLE_GRAPH WHERE predicate LIKE '%allergic%' OR predicate LIKE '%alergi%'",
                    null
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        allergies.add("${cursor.getString(0)} is allergic to ${cursor.getString(1)}")
                    }
                }
                if (allergies.isEmpty()) "" else allergies.joinToString(", ")
            }
            QueryResult.Success(result)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_read")
        } catch (e: Exception) {
             Timber.e(e, "Failed to query allergies.")
             QueryResult.Error(e)
        }
    }

    suspend fun semanticSearch(keyword: String, limit: Int = 10): QueryResult<List<String>> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext QueryResult.Success(emptyList())
        
        try {
            val facts = circuitBreaker.execute("graph_memory_read") {
                val resultList = mutableListOf<String>()
                val db = readableDatabase
                val searchTerm = "%${keyword.lowercase()}%"
                db.rawQuery(
                    """SELECT subject, predicate, object FROM $TABLE_GRAPH 
                       WHERE subject LIKE ? COLLATE NOCASE 
                          OR predicate LIKE ? COLLATE NOCASE 
                          OR object LIKE ? COLLATE NOCASE
                       ORDER BY timestamp DESC LIMIT ?""",
                    arrayOf(searchTerm, searchTerm, searchTerm, limit.toString())
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        resultList.add("${cursor.getString(0)} ${cursor.getString(1)} ${cursor.getString(2)}")
                    }
                }
                resultList.toList()
            }
            QueryResult.Success(facts)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_read")
        } catch (e: Exception) {
            Timber.e(e, "Failed to semantic search Knowledge Graph for: $keyword")
            QueryResult.Error(e)
        }
    }

    suspend fun queryUserFacts(limit: Int = 20): QueryResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val facts = circuitBreaker.execute("graph_memory_read") {
                val resultList = mutableListOf<String>()
                val db = readableDatabase
                db.rawQuery(
                    "SELECT subject, predicate, object FROM $TABLE_GRAPH WHERE subject = 'user' COLLATE NOCASE ORDER BY timestamp DESC LIMIT ?",
                    arrayOf(limit.toString())
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        resultList.add("${cursor.getString(0)} ${cursor.getString(1)} ${cursor.getString(2)}")
                    }
                }
                resultList.toList()
            }
            QueryResult.Success(facts)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_read")
        } catch (e: Exception) {
            Timber.e(e, "Failed to query user facts from Knowledge Graph")
            QueryResult.Error(e)
        }
    }

    suspend fun resolveConflictingFact(subject: String, predicate: String, oldObj: String): QueryResult<Unit> = withContext(Dispatchers.IO) {
        try {
            circuitBreaker.execute("graph_memory_write") {
                val db = writableDatabase
                db.delete(
                    TABLE_GRAPH,
                    "subject = ? AND predicate = ? AND object = ? COLLATE NOCASE",
                    arrayOf(subject.lowercase(), predicate.lowercase(), oldObj.lowercase())
                )
                Timber.i("🗑️ GraphRAG: Deleted conflicting fact: [$subject] [$predicate] [$oldObj]")
            }
            QueryResult.Success(Unit)
        } catch (e: CircuitBreakerOpenException) {
            QueryResult.Degraded("Circuit open: ${e.message}", "graph_memory_write")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete conflicting fact from Knowledge Graph")
            QueryResult.Error(e)
        }
    }
}