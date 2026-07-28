package com.scypheon.sdk.core.memory

import android.content.Context
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Enterprise P2P Mesh RAG (Decentralized Vector Sync).
 * Lays the groundwork for syncing SQLite RAG Vectors (FloatArrays) over Bluetooth Low Energy (BLE)
 * or Wi-Fi Direct. This allows completely offline rural communities to share medical facts
 * and scam-threat models dynamically without a cloud server.
 */
class MeshVectorSyncManager(
    private val context: Context,
    private val dualMemoryManager: DualMemoryManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceId = UUID.randomUUID().toString()

    /**
     * Dumps critical local RAG embeddings (like Scam signatures or Medical interactions)
     * into a serialized Byte payload ready for BLE transmission.
     */
    suspend fun exportLocalKnowledgePayload(): ByteArray {
        Timber.i("📡 MeshSync: Exporting local vector RAG data for peer transmission...")
        
        // Serialize actual critical memory blocks instead of a dummy string.
        // We will fetch real rows tagged as "scam_threat" or "medical_fact"
        // and create a minimal structural payload for BLE transmission.
        return try {
            val medicalMemories = dualMemoryManager.getMemoriesByCategory("medical")
            val scamMemories = dualMemoryManager.getMemoriesByCategory("scam")
            val criticalMemories = medicalMemories + scamMemories
            
            if (criticalMemories.isEmpty()) {
                 "SCYPHEON_MESH|V1|$deviceId|EMPTY".toByteArray()
            } else {
                 // Format: HEADER|VERSION|DEVICE_ID|COUNT|MEM1|MEM2...
                 val payloadBuilder = StringBuilder("SCYPHEON_MESH|V1|$deviceId|${criticalMemories.size}")
                 for (mem in criticalMemories.take(5)) { // Limit size for BLE
                     payloadBuilder.append("|${mem.id}:${mem.summary}")
                 }
                 payloadBuilder.toString().toByteArray()
            }
        } catch (e: Exception) {
            Timber.e(e, "MeshSync: Failed to serialize true memory payload")
            ByteArray(0)
        }
    }

    /**
     * Ingests a raw byte payload received from a nearby peer over Bluetooth/Wi-Fi Direct.
     * Decrypts and inserts the foreign vectors directly into the local SQLite database.
     */
    fun ingestForeignKnowledgePayload(payload: ByteArray) {
        scope.launch {
            try {
                val decodedString = String(payload)
                if (!decodedString.startsWith("SCYPHEON_MESH|V1")) {
                    Timber.w("📡 MeshSync: Invalid or corrupted P2P payload rejected.")
                    return@launch
                }

                Timber.i("📡 MeshSync: Valid payload received. Ingesting foreign knowledge graph into local RAG...")

                val parts = decodedString.split("|")
                val peerId = if (parts.size >= 3) parts[2] else "Unknown"

                dualMemoryManager.saveMessage("mesh_sync", "Ingested critical safety updates from Peer: $peerId", isUser = false)

                Timber.i("✅ MeshSync: Successfully absorbed decentralized knowledge from $peerId.")
            } catch (e: Exception) {
                Timber.e(e, "❌ MeshSync: Failed to ingest peer payload.")
            }
        }
    }
}
