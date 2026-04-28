package com.scypheon.sdk.core.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import timber.log.Timber

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
        // [QWEN CRITICAL FIX] Enable Write-Ahead Logging for better concurrency
        db.execSQL("PRAGMA journal_mode=WAL")
        db.execSQL("PRAGMA synchronous=NORMAL")
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
    fun queryConflictingFact(subject: String, predicate: String, newObj: String): String? {
        val db = this.readableDatabase
        return try {
            val cursor = db.rawQuery(
                "SELECT object FROM $TABLE_GRAPH WHERE subject = ? AND predicate = ? AND object != ? COLLATE NOCASE",
                arrayOf(subject.lowercase(), predicate.lowercase(), newObj.lowercase())
            )
            val conflict = if (cursor.moveToFirst()) cursor.getString(0) else null
            cursor.close()
            conflict
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Ingests a new fact into the Knowledge Graph.
     */
    fun addFact(subject: String, predicate: String, obj: String, confidence: Float = 1.0f) {
        val db = this.writableDatabase
        try {
            db.execSQL(
                "INSERT INTO $TABLE_GRAPH (subject, predicate, object, confidence, timestamp) VALUES (?, ?, ?, ?, ?)",
                arrayOf(subject.lowercase(), predicate.lowercase(), obj.lowercase(), confidence, System.currentTimeMillis())
            )
            Timber.i("🕸️ GraphRAG: Ingested Fact: [$subject] -> [$predicate] -> [$obj]")
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert fact into Knowledge Graph")
        } finally {
            // Do not close the database helper here, as it may be used by other concurrent queries
            // in the Swarm ecosystem.
        }
    }

    /**
     * Retrieves all facts related to a specific subject (Entity).
     * E.g., querying "Budi" might return ["budi is allergic to peanuts", "budi loves fishing"].
     */
    fun querySubject(subject: String): List<String> {
        val facts = mutableListOf<String>()
        val db = this.readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT subject, predicate, object FROM $TABLE_GRAPH WHERE subject = ? COLLATE NOCASE ORDER BY timestamp DESC LIMIT 10",
                arrayOf(subject)
            )
            while (cursor.moveToNext()) {
                val s = cursor.getString(0)
                val p = cursor.getString(1)
                val o = cursor.getString(2)
                facts.add("$s $p $o")
            }
            cursor.close()
        } catch (e: Exception) {
            Timber.e(e, "Failed to query Knowledge Graph")
        } finally {
            // Do not close DB to avoid concurrent IO crashing
        }
        return facts
    }

    /**
     * Retrieves the entire raw graph for visualization.
     */
    fun getFullGraph(): List<Triple<String, String, String>> {
        val graph = mutableListOf<Triple<String, String, String>>()
        val db = this.readableDatabase
        try {
            val cursor = db.rawQuery("SELECT subject, predicate, object FROM $TABLE_GRAPH ORDER BY timestamp DESC LIMIT 50", null)
            while (cursor.moveToNext()) {
                graph.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
            }
            cursor.close()
        } catch (e: Exception) {
            Timber.e(e, "Failed to dump Knowledge Graph")
        } finally {
            // Do not close DB to avoid concurrent IO crashing
        }
        return graph
    }

    /**
     * Retrieves all known allergies across the graph.
     */
    fun getAllergies(): String {
        val allergies = mutableListOf<String>()
        val db = this.readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT subject, object FROM $TABLE_GRAPH WHERE predicate LIKE '%allergic%' OR predicate LIKE '%alergi%'",
                null
            )
            while (cursor.moveToNext()) {
                val s = cursor.getString(0)
                val o = cursor.getString(1)
                allergies.add("$s is allergic to $o")
            }
            cursor.close()
        } catch (e: Exception) {
             Timber.e(e, "Failed to query allergies from Knowledge Graph")
        } finally {
            // Do not close DB to avoid concurrent IO crashing
        }
        return if (allergies.isEmpty()) "No known allergies." else allergies.joinToString(", ")
    }
}
