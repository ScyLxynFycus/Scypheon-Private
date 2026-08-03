package com.scypheon.sdk.core.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import timber.log.Timber
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Edge Max: Local GraphRAG Storage.
 * Stores semantic facts as a Knowledge Graph (Subject -> Predicate -> Object) locally in SQLite.
 * This allows the LLM to traverse complex relationships (e.g. "Budi" -> "is allergic to" -> "Peanuts")
 * rather than relying solely on fuzzy vector similarity.
 */
class GraphMemoryManager(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "ScypheonKnowledgeGraph.db"
        const val DATABASE_VERSION = 1
        const val TABLE_GRAPH = "knowledge_graph"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // [SAR Hardening] Standard SQLite initialization for concurrent GraphRAG traversal
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

        // Index for fast traversal
        db.execSQL("CREATE INDEX idx_subject ON $TABLE_GRAPH(subject)")
        db.execSQL("CREATE INDEX idx_object ON $TABLE_GRAPH(object)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GRAPH")
        onCreate(db)
    }

    /**
     * Checks if a new fact contradicts an existing one (Same Subject/Predicate, different Object).
     */
    suspend fun queryConflictingFact(subject: String, predicate: String, newObj: String): String? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        try {
            db.rawQuery(
                "SELECT object FROM $TABLE_GRAPH WHERE subject = ? AND predicate = ? AND object != ? COLLATE NOCASE",
                arrayOf(subject.lowercase(), predicate.lowercase(), newObj.lowercase())
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Ingests a new fact into the Knowledge Graph.
     */
    suspend fun addFact(subject: String, predicate: String, obj: String, confidence: Float = 1.0f) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        try {
            db.execSQL(
                "INSERT INTO $TABLE_GRAPH (subject, predicate, object, confidence, timestamp) VALUES (?, ?, ?, ?, ?)",
                arrayOf(subject.lowercase(), predicate.lowercase(), obj.lowercase(), confidence, System.currentTimeMillis())
            )
            Timber.i("🕸️ GraphRAG: Ingested Fact: [$subject] -> [$predicate] -> [$obj]")
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert fact into Knowledge Graph")
        }
    }

    /**
     * Retrieves all facts related to a specific subject (Entity).
     * E.g., querying "Budi" might return ["budi is allergic to peanuts", "budi loves fishing"].
     */
    suspend fun querySubject(subject: String): List<String> = withContext(Dispatchers.IO) {
        val facts = mutableListOf<String>()
        val db = readableDatabase
        try {
            db.rawQuery(
                "SELECT subject, predicate, object FROM $TABLE_GRAPH WHERE subject = ? COLLATE NOCASE ORDER BY timestamp DESC LIMIT 10",
                arrayOf(subject)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val s = cursor.getString(0)
                    val p = cursor.getString(1)
                    val o = cursor.getString(2)
                    facts.add("$s $p $o")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query Knowledge Graph")
        }
        facts
    }
    
    /**
     * Retrieves all facts matching a specific predicate.
     */
    suspend fun queryByPredicate(predicate: String): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        val facts = mutableListOf<Triple<String, String, String>>()
        val db = readableDatabase
        try {
            db.rawQuery(
                "SELECT subject, predicate, object FROM $TABLE_GRAPH WHERE predicate = ? COLLATE NOCASE",
                arrayOf(predicate)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    facts.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query predicate from Knowledge Graph")
        }
        facts
    }

    /**
     * Retrieves the entire raw graph for visualization.
     */
    suspend fun getFullGraph(): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        val graph = mutableListOf<Triple<String, String, String>>()
        val db = readableDatabase
        try {
            db.rawQuery("SELECT subject, predicate, object FROM $TABLE_GRAPH ORDER BY timestamp DESC LIMIT 50", null).use { cursor ->
                while (cursor.moveToNext()) {
                    graph.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to dump Knowledge Graph")
        }
        graph
    }

    /**
     * Retrieves all known allergies across the graph.
     */
    suspend fun getAllergies(): String = withContext(Dispatchers.IO) {
        val allergies = mutableListOf<String>()
        val db = readableDatabase
        try {
            db.rawQuery(
                "SELECT subject, object FROM $TABLE_GRAPH WHERE predicate LIKE '%allergic%' OR predicate LIKE '%alergi%'",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val s = cursor.getString(0)
                    val o = cursor.getString(1)
                    allergies.add("$s is allergic to $o")
                }
            }
        } catch (e: Exception) {
             Timber.e(e, "Failed to query allergies from Knowledge Graph")
        }
        if (allergies.isEmpty()) "No known allergies." else allergies.joinToString(", ")
    }

    /**
     * [v1.5.0-SAR] Semantic search across ALL graph columns.
     * 
     * The old querySubject() only matched exact subject names.
     * This fails when facts are stored as: user -> likes -> buah naga
     * and the query keyword is "buah naga" (which is the object, not subject).
     * 
     * This method searches subject, predicate, AND object columns so the Oracle
     * can surface relevant facts regardless of which field contains the keyword.
     */
    suspend fun semanticSearch(keyword: String, limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val facts = mutableListOf<String>()
        if (keyword.isBlank()) return@withContext facts
        
        val db = readableDatabase
        try {
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
                    val s = cursor.getString(0)
                    val p = cursor.getString(1)
                    val o = cursor.getString(2)
                    facts.add("$s $p $o")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to semantic search Knowledge Graph for: $keyword")
        }
        facts
    }

    /**
     * [v1.5.0-SAR] Returns ALL known facts about the user.
     * Used by the Oracle to inject comprehensive personal context.
     */
    suspend fun queryUserFacts(limit: Int = 20): List<String> = withContext(Dispatchers.IO) {
        val facts = mutableListOf<String>()
        val db = readableDatabase
        try {
            db.rawQuery(
                "SELECT subject, predicate, object FROM $TABLE_GRAPH WHERE subject = 'user' COLLATE NOCASE ORDER BY timestamp DESC LIMIT ?",
                arrayOf(limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val s = cursor.getString(0)
                    val p = cursor.getString(1)
                    val o = cursor.getString(2)
                    facts.add("$s $p $o")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query user facts from Knowledge Graph")
        }
        facts
    }
}
