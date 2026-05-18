package com.scypheon.sdk.core.humanitarian.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SOSMeshRouter @Inject constructor(
    private val bleMeshNetwork: BleMeshNetwork,
    private val meshDao: MeshDao,
    private val signatureManager: com.scypheon.sdk.core.security.MeshSignatureManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun broadcastCriticalAlert(drugA: String, drugB: String, sessionId: String) {
        val payload = "CRITICAL_INTERACTION: $drugA + $drugB | Trace: $sessionId"
        Timber.i("🚨 [SOS MESH] Broadcasting Critical Alert: $payload")
        
        scope.launch {
            // 1. Sign the payload
            val signature = signatureManager.signData(payload)
            val packetId = java.util.UUID.randomUUID().toString()
            
            val entity = MeshMessageEntity(
                packetId = packetId,
                senderDeviceId = "DEVICE_${signature.take(8)}", 
                payload = payload,
                timestamp = System.currentTimeMillis(),
                relayCount = 0,
                signature = signature
            )
            meshDao.insertMessage(entity)
            
            // 2. Transmit via BLE (Packet format: packetId|signature|payload)
            val fullPacket = "$packetId|$signature|$payload"
            bleMeshNetwork.broadcastMessage(fullPacket)
        }
    }
}
