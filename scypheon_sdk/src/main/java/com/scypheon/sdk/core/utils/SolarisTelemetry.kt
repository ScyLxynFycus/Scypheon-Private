package com.scypheon.sdk.core.utils

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * 🛡️ SOLARIS TELEMETRY: Async Batched NDJSON Flusher
 * Decouples ML inference from disk I/O to maintain deterministic SLOs.
 */
object SolarisTelemetry {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channel = Channel<String>(capacity = 500, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    
    private var telemetryFile: File? = null
    private const val MAX_BYTES = 5L * 1024 * 1024 // 5MB Production Cap
    private val bytesWritten = AtomicLong(0)

    fun init(context: Context) {
        val dir = context.noBackupFilesDir
        telemetryFile = File(dir, "telemetry.ndjson")
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
                    // Wait for first entry or timeout
                    val entry = withTimeoutOrNull(2000) { channel.receive() }
                    if (entry != null) {
                        batch.add(entry)
                    }

                    // Drain remaining available or wait until batch reaches 50
                    while (batch.size < 50) {
                        val next = channel.tryReceive().getOrNull() ?: break
                        batch.add(next)
                    }

                    if (batch.isNotEmpty()) {
                        flushBatch(batch)
                        batch.clear()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "🚨 [SOLARIS] Telemetry flusher encounter error")
                }
            }
        }
    }

    private suspend fun flushBatch(batch: List<String>) = withContext(Dispatchers.IO) {
        val file = telemetryFile ?: return@withContext
        try {
            // 🛡️ RING BUFFER MAINTENANCE: Atomic rotation at 5MB
            if (file.length() > MAX_BYTES) {
                rotateFile(file)
            }

            val data = batch.joinToString("\n") + "\n"
            file.appendText(data)
            bytesWritten.addAndGet(data.length.toLong())
        } catch (e: Exception) {
            Timber.e(e, "🚨 [SOLARIS] Failed to flush telemetry batch")
        }
    }

    private fun rotateFile(file: File) {
        Timber.w("🔄 [SOLARIS] Telemetry limit reached. Pruning oldest 30%.")
        try {
            val lines = file.readLines()
            if (lines.size > 100) {
                val keep = lines.drop(lines.size * 3 / 10)
                file.writeText(keep.joinToString("\n") + "\n")
            } else {
                file.writeText("") // Clear if too small to prune
            }
        } catch (e: IOException) {
            file.delete()
        }
    }

    /**
     * Records a metric asynchronously. Non-blocking.
     */
    fun record(metric: String, valueMs: Long, metadata: Map<String, String>? = null) {
        val metaJson = metadata?.let { 
            "," + it.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" } 
        } ?: ""
        val entry = """{"ts":${System.currentTimeMillis()},"metric":"$metric","value_ms":$valueMs$metaJson}"""
        val result = channel.trySend(entry)
        if (result.isFailure) {
            // Drop happens automatically due to DROP_OLDEST, but we log for trace debugging
            Timber.d("⚠️ [SOLARIS] Telemetry channel saturated. Metric dropped.")
        }
    }
}
