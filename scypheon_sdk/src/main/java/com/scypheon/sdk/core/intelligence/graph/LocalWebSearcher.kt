package com.scypheon.sdk.core.intelligence.graph

import androidx.room.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocalWebSearcher (HELIOS L3 / Oracle Level 3):
 * Implements high-performance document research using SQLite FTS5 (Full-Text Search).
 * Allows the Oracle to "research" thousands of local PDF/Text summaries instantly.
 */

@Fts4 // SQLite FTS4/5 for indexed text search
@Entity(tableName = "local_docs_index")
data class DocumentIndex(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int? = null,
    val docId: String,
    val content: String, // The actual text content for searching
    val source: String,  // e.g., "WHO_SOP.pdf"
    val category: String
)

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoc(doc: DocumentIndex)

    /**
     * The "Riset Dokumen" logic.
     * Uses SQLite MATCH operator for sub-10ms keyword search across thousands of pages.
     */
    @Query("SELECT * FROM local_docs_index WHERE local_docs_index MATCH :query")
    suspend fun research(query: String): List<DocumentIndex>
}

@Singleton
class LocalWebSearcher @Inject constructor(
    private val docDao: DocumentDao
) {
    suspend fun deepResearch(query: String): List<String> {
        Timber.i("📚 [LOCAL_WEB] Deep researching documents for: $query")
        
        // Clean query for FTS (SQLite MATCH syntax)
        val cleanQuery = query.split(" ")
            .filter { it.length > 3 }
            .joinToString(" OR ") { "$it*" }

        return try {
            val results = docDao.research(cleanQuery)
            Timber.d("📚 [LOCAL_WEB] Found ${results.size} relevant document snippets.")
            results.map { "[SOURCE: ${it.source}] ${it.content.take(300)}..." }
        } catch (e: Exception) {
            Timber.e(e, "Local research failed")
            emptyList()
        }
    }
}
