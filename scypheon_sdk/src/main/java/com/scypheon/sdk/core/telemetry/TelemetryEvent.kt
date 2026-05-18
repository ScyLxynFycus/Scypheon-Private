package com.scypheon.sdk.core.telemetry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telemetry_events",
    indices = [Index(value = ["eventId", "timestamp"], unique = true)]
)
data class TelemetryEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: String, // Global unique ID for sync
    val type: String,
    val payload: String,
    val timestamp: Long, // LWW uses this
    val synced: Boolean
)
