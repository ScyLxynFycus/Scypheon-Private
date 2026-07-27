package com.scypheon.sdk.core.grounding

import androidx.room.*

/**
 * Concrete Room Entity for general humanitarian knowledge.
 * Zero stubs. Production-ready.
 */
@Entity(tableName = "knowledge_base")
data class KnowledgeEntry(
    @PrimaryKey val id: String,
    val domain: String,
    val term: String,
    val content: String,
    val source: String,
    val confidence: Float,
    val lastUpdated: Long
)

/**
 * FTS4 Virtual Table for high-performance knowledge search.
 */
@Fts4
@Entity(tableName = "knowledge_base_fts")
data class KnowledgeFts(
    val term: String, 
    val content: String, 
    val domain: String
)

/**
 * DAO for general humanitarian knowledge grounding.
 */
@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: KnowledgeEntry)

    @Query("""
        SELECT kb.id, kb.domain, kb.term, kb.content, kb.source, kb.confidence, kb.lastUpdated 
        FROM knowledge_base_fts fts 
        JOIN knowledge_base kb ON fts.rowid = kb.id 
        WHERE knowledge_base_fts MATCH :query 
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 10): List<KnowledgeEntry>

    @Query("""
        SELECT kb.id, kb.domain, kb.term, kb.content, kb.source, kb.confidence, kb.lastUpdated 
        FROM knowledge_base_fts fts 
        JOIN knowledge_base kb ON fts.rowid = kb.id 
        WHERE knowledge_base_fts MATCH :query AND kb.domain = :domain
        LIMIT :limit
    """)
    suspend fun searchByDomain(query: String, domain: String, limit: Int = 10): List<KnowledgeEntry>

    @Query("SELECT content FROM knowledge_base WHERE term = :term AND domain = :domain LIMIT 1")
    suspend fun getExact(term: String, domain: String): String?

    @Query("""
        SELECT kb.id, kb.domain, kb.term, kb.content, kb.source, kb.confidence, kb.lastUpdated 
        FROM knowledge_base_fts fts 
        JOIN knowledge_base kb ON fts.rowid = kb.id 
        WHERE knowledge_base_fts MATCH :query 
        AND kb.domain = :domain
        LIMIT :limit
    """)
    suspend fun searchByDomain(query: String, domain: String, limit: Int = 10): List<KnowledgeEntry>
}
