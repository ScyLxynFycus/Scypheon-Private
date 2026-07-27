package com.scypheon.sdk.core.humanitarian.medical

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pharmacopeia")
data class PharmacopeiaEntry(
    @PrimaryKey val id: String,
    val drugName: String,
    val genericName: String? = null,
    val dosage: String,
    val indications: String,
    val contraindications: String,
    val interactionDetails: String? = null,
    val maxMgPerKg: Float? = null,
    val maxDailyMg: Int? = null,
    val severity: String = "MODERATE", // Stored as String to avoid Room TypeConverter complexity
    val source: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val content: String = "" // Required for FTS5 synchronization
)

@androidx.room.Fts4
@Entity(tableName = "pharmacopeia_fts")
data class PharmacopeiaFts(
    val drugName: String,
    val genericName: String?,
    val indications: String,
    val contraindications: String,
    val content: String
)
