package com.scypheon.sdk.core.agent.context

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "context_sessions")
data class ContextSessionEntity(
    @PrimaryKey val sessionId: String,
    val segmentsRaw: String, // Delimited format: id|priority|content|tokens|timestamp\n
    val lastUpdatedMs: Long
)

@Dao
interface ContextSessionDao {
    @Upsert
    suspend fun save(entity: ContextSessionEntity)
    
    @Query("SELECT * FROM context_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun load(sessionId: String): ContextSessionEntity?
    
    @Query("DELETE FROM context_sessions WHERE sessionId = :sessionId")
    suspend fun clear(sessionId: String)
}
