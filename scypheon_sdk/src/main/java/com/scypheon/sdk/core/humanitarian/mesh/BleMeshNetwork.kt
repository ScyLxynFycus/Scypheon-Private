package com.scypheon.sdk.core.humanitarian.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.scypheon.sdk.core.annotations.SafetyCritical
import com.scypheon.sdk.core.utils.CryptoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@SafetyCritical
@Singleton
// Suppress izin sementara untuk Hackathon. Pastikan Anda meminta izin BLUETOOTH_SCAN & BLUETOOTH_ADVERTISE di Activity!
@SuppressLint("MissingPermission") 
class BleMeshNetwork @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshDao: MeshDao
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val bleAdvertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private val SERVICE_UUID = ParcelUuid(UUID.fromString("6a4f83b1-1234-4a5e-b8ab-ad01a79e360e"))
    private val MESH_SECRET = "AuraLink_Enterprise_2026"

    private val _receivedMessages = MutableSharedFlow<SecureMeshMessage>(extraBufferCapacity = 50)
    val receivedMessages: SharedFlow<SecureMeshMessage> = _receivedMessages.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Data class baru untuk mencegah Memory Leak pada pesan yang tidak lengkap
    private data class AssemblyState(
        val totalChunks: Int,
        val chunks: Array<String?>,
        var lastUpdated: Long = System.currentTimeMillis()
    )
    private val assemblyBuffer = ConcurrentHashMap<String, AssemblyState>()
    private val CHUNK_TTL_MS = 60_000L // Hapus memori jika chunk tidak lengkap setelah 60 detik

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord ?: return
            
            if (scanRecord.serviceUuids?.contains(SERVICE_UUID) == true) {
                val rawData = scanRecord.serviceData[SERVICE_UUID] ?: return
                scope.launch { processPacket(device.address, rawData) }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Timber.e("❌ BLE Mesh: Scan Failed with code: $errorCode")
        }
    }

    private val MAX_RELAYS = 2
    private val CHUNK_SIZE = 8 // Diperkecil drastis agar tidak melebihi 31 Bytes BLE Limit

    private suspend fun processPacket(senderAddr: String, data: ByteArray) {
        try {
            val raw = String(data)
            val parts = raw.split("|")
            if (parts.size < 5) return

            val packetId = parts[0]
            val signature = parts[1]
            val chunkIdx = parts[2].toIntOrNull() ?: 0
            val totalChunks = parts[3].toIntOrNull() ?: 1
            val payloadChunk = parts[4]

            // 1. Deduplication Gate
            if (meshDao.getMessageByPacketId(packetId) != null) return

            // 2. Assembly Gate dengan pembersihan (Garbage Collection) manual
            cleanUpStaleBuffers()
            val fullPayload = if (totalChunks > 1) {
                assembleChunks(packetId, chunkIdx, totalChunks, payloadChunk) ?: return
            } else {
                payloadChunk
            }

            // 3. Integrity Gate
            if (!CryptoUtils.verifyPacket(fullPayload, signature, MESH_SECRET)) {
                Timber.w("⚠️ BLE Mesh: Rejected spoofed packet from $senderAddr")
                return
            }

            Timber.i("📡 BLE Mesh: Packet $packetId verified!")
            
            // 4. Database Persistance & UI Emittion
            val entity = MeshMessageEntity(
                packetId = packetId,
                senderDeviceId = senderAddr,
                payload = fullPayload,
                timestamp = System.currentTimeMillis(),
                relayCount = 1,
                signature = signature
            )
            meshDao.insertMessage(entity)
            _receivedMessages.emit(SecureMeshMessage(packetId, senderAddr, fullPayload, signature))
            
            // 5. MESH RELAY PROTOCOL (Flood Routing)
            if (entity.relayCount < MAX_RELAYS) {
                Timber.d("🔄 BLE Mesh: Relaying packet to other nodes...")
                broadcastMessage(fullPayload, packetId, signature, entity.relayCount + 1)
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ BLE Mesh: Decode fault")
        }
    }

    private fun assembleChunks(packetId: String, idx: Int, total: Int, content: String): String? {
        val state = assemblyBuffer.getOrPut(packetId) { AssemblyState(total, arrayOfNulls(total)) }
        state.chunks[idx] = content
        state.lastUpdated = System.currentTimeMillis()
        
        return if (state.chunks.all { it != null }) {
            val result = state.chunks.joinToString("")
            assemblyBuffer.remove(packetId)
            result
        } else null
    }

    private fun cleanUpStaleBuffers() {
        val now = System.currentTimeMillis()
        assemblyBuffer.entries.removeIf { now - it.value.lastUpdated > CHUNK_TTL_MS }
    }

    suspend fun broadcastMessage(
        payload: String, 
        existingPacketId: String? = null, 
        existingSignature: String? = null,
        relayCount: Int = 0
    ) {
        if (bleAdvertiser == null) return

        val packetId = existingPacketId ?: UUID.randomUUID().toString().take(4) // Persingkat ID
        val signature = existingSignature ?: CryptoUtils.signPacket(payload, MESH_SECRET).take(4) // Persingkat Signature
        
        val chunks = payload.chunked(CHUNK_SIZE)
        val total = chunks.size

        // 🛑 CRITICAL FIX: Loop dengan Hardware Release (Mencegah Bluetooth Hancur)
        chunks.forEachIndexed { index, chunk ->
            val fullPacket = "$packetId|$signature|$index|$total|$chunk"
            
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // Maksimalkan jarak (hingga 100m)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(SERVICE_UUID)
                .addServiceData(SERVICE_UUID, fullPacket.toByteArray(Charsets.UTF_8))
                .build()

            var adCallback: AdvertiseCallback? = null
            adCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Timber.d("📡 BLE Mesh: Transmitting chunk $index/$total [${fullPacket.toByteArray().size} bytes]")
                }
                override fun onStartFailure(errorCode: Int) {
                    Timber.e("❌ BLE Mesh: Hardware rejected chunk $index. Error: $errorCode")
                }
            }

            try {
                bleAdvertiser.startAdvertising(settings, data, adCallback)
                delay(600) // Pancarkan sinyal selama 600 milidetik agar tertangkap scanner terdekat
            } finally {
                // 🛑 WAJIB: Bebaskan slot hardware Bluetooth
                bleAdvertiser.stopAdvertising(adCallback)
                delay(150) // Beri jeda hardware sebelum menembakkan chunk berikutnya
            }
        }
    }

    fun startScanning() {
        if (bleScanner == null) return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val filter = ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build()
        bleScanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
    }
}

data class SecureMeshMessage(val packetId: String, val senderId: String, val payload: String, val signature: String)
