package com.scypheon.sdk.core.telemetry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TelemetryEvent)

    @Query("SELECT * FROM telemetry_events WHERE synced = 0 LIMIT :limit")
    suspend fun getUnsynced(limit: Int): List<TelemetryEvent>

    @Query("UPDATE telemetry_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM telemetry_events WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int
}
