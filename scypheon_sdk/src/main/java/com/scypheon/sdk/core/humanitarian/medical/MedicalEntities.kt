package com.scypheon.sdk.core.humanitarian.medical

import androidx.room.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Authoritative medical entities for the Scypheon SDK.
 * Restored and hardened to ensure zero-stub runtime.
 */

@Entity(tableName = "medical_vectors")
data class MedicalVectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceId: String, 
    val sourceType: String, // "DRUG" or "PROTOCOL"
    val embedding: ByteArray
)

@Entity(tableName = "first_aid_protocols")
data class FirstAidEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conditionName: String,
    val conditionNameId: String,
    val severityLevel: Int = 1,
    val instructionsEn: String,
    val instructionsId: String,
    val medicationRequired: String?,
    val warningEn: String?,
    val warningId: String?,
    val localSearchKeywords: String,
    val source: String,
    val lastUpdated: String
)

@Entity(tableName = "drug_interactions",
    primaryKeys = ["drugAId", "drugBId"],
    indices = [Index("drugAId"), Index("drugBId")])
data class InteractionEntity(
    val drugAId: String,
    val drugBId: String,
    val interactionType: String,
    val severity: String,
    val descriptionEn: String,
    val descriptionId: String,
    val source: String = "WHO EML 23"
)

@Entity(tableName = "pharmacopeia_metadata")
data class PharmacopeiaMetadata(
    @PrimaryKey val id: Int = 0,
    val version: String,
    val sourceAgency: String = "WHO",
    val signedHash: String,
    val expiryDate: Long,
    val recordCount: Int
)

class MedicalTypeConverters {
    @TypeConverter
    fun fromFloatArray(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * 4).apply {
            order(ByteOrder.nativeOrder())
            array.forEach { putFloat(it) }
        }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        val arr = FloatArray(bytes.size / 4)
        for (i in arr.indices) arr[i] = buffer.float
        return arr
    }

    @TypeConverter
    fun fromMemoryTier(tier: com.scypheon.sdk.core.memory.MemoryTier): String {
        return tier.name
    }

    @TypeConverter
    fun toMemoryTier(name: String): com.scypheon.sdk.core.memory.MemoryTier {
        return com.scypheon.sdk.core.memory.MemoryTier.valueOf(name)
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        return list?.joinToString(separator = "|") ?: ""
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrEmpty()) return emptyList()
        return data.split("|")
    }
}
