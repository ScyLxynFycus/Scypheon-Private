package com.scypheon.sdk.core.agent

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "agent_checkpoints")
data class AgentCheckpoint(
    @PrimaryKey val sessionId: String,
    val input: String,
    val historyJson: String,
    val stateJson: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Dao
interface AgentCheckpointDao {
    @Upsert
    suspend fun save(checkpoint: AgentCheckpoint)
    
    @Query("SELECT * FROM agent_checkpoints WHERE sessionId = :sessionId LIMIT 1")
    suspend fun load(sessionId: String): AgentCheckpoint?
    
    @Query("DELETE FROM agent_checkpoints WHERE sessionId = :sessionId")
    suspend fun clear(sessionId: String)

    @Query("SELECT * FROM agent_checkpoints ORDER BY timestampMs DESC")
    suspend fun getAll(): List<AgentCheckpoint>
}

