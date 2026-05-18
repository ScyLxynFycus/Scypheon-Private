package com.scypheon.sdk.core.humanitarian.maps

import androidx.room.*

@Entity(tableName = "map_tiles")
data class MapTile(
    @PrimaryKey val tileId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MapTileDao {
    @Upsert
    suspend fun insertTile(tile: MapTile)

    @Query("SELECT * FROM map_tiles WHERE tileId = :tileId LIMIT 1")
    suspend fun getTile(tileId: String): MapTile?

    @Query("SELECT COUNT(*) FROM map_tiles")
    suspend fun getTileCount(): Int

    @Query("SELECT SUM(LENGTH(data)) FROM map_tiles")
    suspend fun getTotalCacheSize(): Long

    @Query("DELETE FROM map_tiles WHERE tileId IN (SELECT tileId FROM map_tiles ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}
