package com.scypheon.sdk.core.humanitarian.mesh

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MeshDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MeshMessageEntity): Long

    @Query("SELECT * FROM mesh_messages WHERE packetId = :packetId LIMIT 1")
    suspend fun getMessageByPacketId(packetId: String): MeshMessageEntity?

    @Query("SELECT * FROM mesh_messages ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentMessages(): List<MeshMessageEntity>

    @Query("SELECT COUNT(*) FROM mesh_messages")
    suspend fun getMessageCount(): Int
}
