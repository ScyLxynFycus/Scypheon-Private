package com.scypheon.sdk.core.utils

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

/**
 * 🛰️ SOLARIS TELEMETRY: Hardened Async Batched NDJSON Flusher
 * Implements atomic persistence and crash-resilient metrics for Edge AI.
 * [v1.5.2-HARDENED]
 */
object SolarisTelemetry {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channel = Channel<String>(capacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var telemetryFile: File? = null
    private var rootDir: File? = null
    private const val MAX_BYTES = 10L * 1024 * 1024 // 10MB Production Cap
    private val bytesWritten = AtomicLong(0)

    fun init(context: Context) {
        rootDir = context.noBackupFilesDir
        telemetryFile = File(rootDir, "telemetry.ndjson")
        if (telemetryFile?.exists() == true) {
            bytesWritten.set(telemetryFile!!.length())
        }

        startFlusher()
    }

    private fun startFlusher() {
        scope.launch {
            val batch = mutableListOf<String>()
            while (isActive) {
                try {
                    // Optimized draining: wait for first entry or timeout
                    val entry = withTimeoutOrNull(2500) { channel.receive() }
                    if (entry != null) {
                        batch.add(entry)
                    }

                    // Aggressive batching for high-throughput inference
                    while (batch.size < 100) {
                        val next = channel.tryReceive().getOrNull() ?: break
                        batch.add(next)
                    }

                    if (batch.isNotEmpty()) {
                        flushBatchAtomic(batch)
                        batch.clear()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "🚨 [SOLARIS] Telemetry flusher encountered a critical error")
                }
            }
        }
    }

    /**
     * Hardened persistence: Uses append-only for speed but manages rotation
     * with atomic file operations to prevent corruption during NPU-induced SoC hangs.
     */
    private suspend fun flushBatchAtomic(batch: List<String>) = withContext(Dispatchers.IO) {
        val file = telemetryFile ?: return@withContext
        try {
            if (file.length() > MAX_BYTES) {
                rotateFileHardened(file)
            }

            val data = batch.joinToString("\n") + "\n"
            file.appendText(data)
            bytesWritten.addAndGet(data.length.toLong())
        } catch (e: Exception) {
            Timber.e(e, "🚨 [SOLARIS] Failed to persist telemetry batch")
        }
    }

    private fun rotateFileHardened(file: File) {
        Timber.w("🚨 [SOLARIS] Telemetry rotation triggered. Enforcing atomic prune.")
        try {
            val tempFile = File(rootDir, "telemetry.tmp.${UUID.randomUUID()}")
            val lines = file.readLines()
            
            if (lines.size > 200) {
                // Keep the most recent 60%
                val keep = lines.drop(lines.size * 4 / 10)
                tempFile.bufferedWriter().use { writer ->
                    keep.forEach { 
                        writer.write(it)
                        writer.newLine()
                    }
                }
                
                if (tempFile.renameTo(file)) {
                    bytesWritten.set(file.length())
                } else {
                    // Fallback if rename fails (e.g. file lock)
                    file.writeText(keep.joinToString("\n") + "\n")
                }
            } else {
                file.writeText("") // Reset if too fragmented
                bytesWritten.set(0)
            }
            tempFile.delete()
        } catch (e: Exception) {
            Timber.e(e, "🚨 [SOLARIS] Rotation failed. Purging to save disk space.")
            file.delete()
            bytesWritten.set(0)
        }
    }

    /**
     * Records a hardened metric. Includes process and thread metadata.
     */
    fun record(metric: String, valueMs: Long, metadata: Map<String, String>? = null) {
        val tid = Thread.currentThread().id
        val baseMeta = mapOf(
            "tid" to tid.toString(),
            "ver" to "1.5.2"
        )
        
        val fullMeta = metadata?.let { baseMeta + it } ?: baseMeta
        val metaJson = "," + fullMeta.entries.joinToString(",") { (k, v) -> 
            val cleanV = v.replace("\"", "\\\"")
            "\"$k\":\"$cleanV\"" 
        }
        
        val entry = """{"ts":${System.currentTimeMillis()},"metric":"$metric","value":$valueMs$metaJson}"""
        
        if (!channel.trySend(entry).isSuccess) {
            // Buffer overflow is handled by DROP_OLDEST, but we record the event for audit
            Timber.d("⚠️ [SOLARIS] Telemetry buffer saturated at 1000 items. Dropping oldest.")
        }
    }
}
