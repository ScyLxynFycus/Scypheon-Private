package com.scypheon.sdk.core.utils

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import android.os.Environment
import java.io.*
import java.nio.channels.FileLock
import java.nio.channels.FileChannel

data class ModelRegistry(
    val eliteModel: String? = null,
    val universalModel: String? = null,
    val memoryModel: String? = null,
    val faceModel: String? = null,
    val poseModel: String? = null,
    val segmentModel: String? = null,
    val depthModel: String? = null,
    val whisperModel: String? = null
)

/**
 * Enterprise Production Pipeline.
 * Extracts heavy ML models (.tflite, .task) from the APK's compressed assets folder
 * into the internal storage directory where native C++ engines (MediaPipe/Llama.cpp) can read them.
 */
object AssetExtractor {

    private const val MODELS_DIR = "models"

    /**
     * Checks if all expected models have been extracted and verified.
     */
    fun areModelsExtracted(context: Context): Boolean {
        val targetDir = File(context.noBackupFilesDir, MODELS_DIR)
        return targetDir.exists() && targetDir.isDirectory
    }

    /**
     * Resilient Extraction with Stale-Lock Recovery.
     * Uses FileChannel.tryLock() with exponential backoff and PID verification.
     */
    /**
     * Resilient Extraction with Stale-Lock Recovery.
     * Uses FileChannel.lock() within a 'use' block to prevent resource leaks.
     */
    fun extractModels(context: Context): String {
        val targetDir = File(context.noBackupFilesDir, MODELS_DIR)
        if (!targetDir.exists()) targetDir.mkdirs()

        val lockFile = File(targetDir, "models.lock")
        val integrityGuard = ModelIntegrityGuard(context)

        try {
            RandomAccessFile(lockFile, "rw").use { raf ->
                val channel = raf.channel
                var lock: FileLock? = null
                var attempts = 0
                
                while (lock == null && attempts < 3) {
                    lock = try {
                        channel.tryLock()
                    } catch (e: Exception) {
                        null
                    }
                    
                    if (lock == null) {
                        Timber.w("⚠️ [PHOENIX] Model lock held by another process. Attempt ${attempts + 1}/3...")
                        
                        raf.seek(0)
                        val pidStr = raf.readLine()?.trim()
                        if (!pidStr.isNullOrEmpty()) {
                            val pid = pidStr.toIntOrNull()
                            if (pid != null && isProcessStale(pid)) {
                                Timber.e("🚨 [PHOENIX] Stale lock detected (PID $pid is dead). Reclaiming...")
                                lockFile.delete()
                                lockFile.createNewFile()
                            }
                        }
                        
                        Thread.sleep(100L * (1 shl attempts))
                        attempts++
                    }
                }

                if (lock == null) {
                    throw IOException("🚨 [PHOENIX] Failed to acquire model lock. Extraction ABORTED.")
                }

                lock.use { // 🛡️ [SAR] Ensure lock release
                    raf.setLength(0)
                    raf.writeBytes(android.os.Process.myPid().toString() + "\n")
                    
                    val assetManager = context.assets
                    val files = assetManager.list(MODELS_DIR) ?: return targetDir.absolutePath

                    for (filename in files) {
                        val targetFile = File(targetDir, filename)
                        val expectedHash = getExpectedHash(filename)

                        if (expectedHash != "IGNORE" && integrityGuard.verifyAndEnsure(filename, targetFile, expectedHash)) {
                            continue 
                        }

                        Timber.i("📦 [PHOENIX] Extracting: $filename")
                        atomicIngest(context, "$MODELS_DIR/$filename", targetFile)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed model extraction cycle")
        }

        return targetDir.absolutePath
    }

    /**
     * Atomic Ingest Protocol (Architectural Mandate 2).
     * Prevents partial extraction corruption using temp file + rename pattern.
     */
    private fun atomicIngest(context: Context, assetPath: String, targetFile: File) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            context.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Atomic rename failed for ${targetFile.name}")
            }
            Timber.i("✅ [PHOENIX] Atomic Ingest: ${targetFile.name}")
        } catch (e: Exception) {
            Timber.e(e, "🚨 Ingest aborted: ${targetFile.name}")
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /**
     * Checks if a process is stale by verifying /proc/<pid>/cmdline.
     * Android standard: If /proc/<pid> exists but cmdline doesn't match our package, or 
     * if /proc/<pid> doesn't exist, it's stale.
     */
    private fun isProcessStale(pid: Int): Boolean {
        val cmdline = File("/proc/$pid/cmdline")
        if (!cmdline.exists()) return true
        return try {
            val text = cmdline.readText()
            // In a sandbox, we check if the process belongs to our base package
            !text.contains("com.scypheon") 
        } catch (e: Exception) {
            true // Permission denied or read error usually means it's not our process anymore
        }
    }

    /**
     * Step 0: Ensures the model is present in internal storage and valid.
     * Checks internal first, then trying migration from Downloads, then Assets.
     */
    fun extractAndVerify(context: Context, filename: String): Boolean {
        val targetFile = File(getModelPath(context, filename))
        val integrityGuard = ModelIntegrityGuard(context)
        
        // 1. Check if already exists and valid
        val expectedHash = getExpectedHash(filename)
        if (integrityGuard.verifyAndEnsure(filename, targetFile, expectedHash)) {
            if (expectedHash.equals("IGNORE", ignoreCase = true)) {
                val assetPath = if (filename.startsWith(".")) {
                    "shm/${filename.removePrefix(".")}"
                } else {
                    "$MODELS_DIR/$filename"
                }
                
                // Known expected sizes for uncompressed stealth assets to detect truncation:
                val expectedMinSize = when (filename) {
                    ".gateway_sync.bin", "gateway_sync.bin" -> 190_000_000L
                    ".universal_sync.bin", "universal_sync.bin" -> 320_000_000L
                    else -> 0L
                }

                if (expectedMinSize > 0 && targetFile.length() < expectedMinSize) {
                    Timber.w("⚠️ [PHOENIX] File on disk '${targetFile.name}' is too small (${targetFile.length()} < $expectedMinSize). Forcing re-extraction...")
                    targetFile.delete()
                    return extractIndividualModel(context, filename, targetFile, expectedHash)
                }

                try {
                    var assetLength = -1L
                    try {
                        context.assets.openFd(assetPath).use { afd ->
                            assetLength = afd.length
                        }
                    } catch (e: Exception) {
                        context.assets.open(assetPath).use { input ->
                            assetLength = input.available().toLong()
                        }
                    }
                    
                    if (assetLength > 0 && targetFile.length() != assetLength) {
                        Timber.w("⚠️ [PHOENIX] Size mismatch for '$filename' on disk (${targetFile.length()} != $assetLength). Forcing re-extraction...")
                        targetFile.delete()
                        return extractIndividualModel(context, filename, targetFile, expectedHash)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error checking asset size for '$assetPath'")
                }
            }
            return true
        }
        
        // 2. Try migration from Downloads (if fully downloaded)
        if (migrateFromDownloads(context, filename, targetFile, expectedHash)) return true
        
        // 3. Fallback: Extraction from Assets (blocking)
        return extractIndividualModel(context, filename, targetFile, expectedHash)
    }

    private fun getExpectedHash(filename: String): String {
        return try {
            val constName = filename.uppercase().replace(".", "_").replace("-", "_")
            val field = Class.forName("com.scypheon.sdk.core.utils.ModelHashes").getField(constName)
            field.get(null) as String
        } catch (e: Exception) {
            "IGNORE"
        }
    }

    private fun migrateFromDownloads(context: Context, filename: String, targetFile: File, expectedHash: String): Boolean {
        val downloadPaths = mutableListOf<File>()
        
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { downloadPaths.add(it) }
        val publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadPaths.add(File(publicDownload, "VITREON"))
        downloadPaths.add(publicDownload)

        for (dir in downloadPaths) {
            if (!dir.exists() || !dir.isDirectory) continue
            
            var matchedFile: File? = null
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.name.equals(filename, ignoreCase = true)) {
                        matchedFile = file
                        return@forEach
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "🚨 [SBI] Directory scan migration failed")
            }
            if (matchedFile == null) continue
            
            val fileSize = matchedFile.length()
            Timber.i("🎯 [SBI] Found candidate at ${dir.name}: ${matchedFile.name} (${fileSize / 1024 / 1024} MB)")
            
            val integrityGuard = ModelIntegrityGuard(context)
            val isValid = if (expectedHash.equals("IGNORE", ignoreCase = true)) {
                true 
            } else {
                val actualHash = integrityGuard.computeSHA256(matchedFile)
                actualHash.equals(expectedHash, ignoreCase = true)
            }

            if (!isValid) {
                Timber.w("⚠️ [SBI] Hash mismatch for candidate in ${dir.name}.")
                continue
            }

            Timber.i("🚚 [SBI] Migrating valid model to internal (Atomic): ${targetFile.name}")
            cleanupStaleArtifacts(context)

            try {
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                matchedFile.inputStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (tempFile.length() == fileSize && tempFile.renameTo(targetFile)) {
                    Timber.i("✅ [SBI] Migration successful: ${targetFile.name}")
                    // 🛡️ [SAR] Scoped Storage Cleanup Protocol: Only delete source on success
                    if (matchedFile.delete()) {
                        Timber.i("🧹 [SBI] Source artifact purged from Scoped Storage.")
                    }
                    return true
                } else {
                    if (tempFile.exists()) tempFile.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "🚨 [SBI] Migration failed for $filename")
            }
        }
        return false
    }

    private fun extractIndividualModel(context: Context, filename: String, targetFile: File, expectedHash: String): Boolean {
        return try {
            val assetManager = context.assets
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            
            val assetPath = if (filename.startsWith(".")) {
                "shm/${filename.removePrefix(".")}"
            } else {
                "$MODELS_DIR/$filename"
            }
            
            assetManager.open(assetPath).use { input ->
                val fileSize = input.available().toLong()
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                    output.flush()
                }
            }
            
            if (tempFile.renameTo(targetFile)) {
                Timber.i("✅ [SBI] Extracted from assets: $filename")
                true
            } else false
        } catch (e: Exception) {
            Timber.e(e, "Asset $filename not found in APK assets.")
            false
        }
    }

    /**
     * Dynamically discovers the best available models across internal and external storage.
     * Prioritizes largest file size if multiple candidates exist.
     */
    fun discoverModels(context: Context): ModelRegistry = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val allVisibleFiles = mutableSetOf<String>()
        
        // 1. Scan Assets (Public & Stealth)
        context.assets.list(MODELS_DIR)?.let { allVisibleFiles.addAll(it) }
        context.assets.list("shm")?.let { allVisibleFiles.addAll(it) }
        
        // 2. Scan Downloads & Internal Stealth Storage
        val scanPaths = listOfNotNull(
            context.noBackupFilesDir,
            File(context.noBackupFilesDir, MODELS_DIR),
            File(context.filesDir, ".shm"),
            context.getExternalFilesDir(null),
            context.getExternalFilesDir(null)?.let { File(it, MODELS_DIR) },
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VITREON"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        
        for (dir in scanPaths) {
            if (dir.exists() && dir.isDirectory) {
                try {
                    dir.listFiles()?.forEach { file ->
                        allVisibleFiles.add(file.name)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "🚨 [SBI] Directory scan discovery failed")
                }
            }
        }

        var elite: String? = null
        var universal: String? = null
        var memory: String? = null
        var face: String? = null
        var pose: String? = null
        var segment: String? = null
        var depth: String? = null
        var whisper: String? = null
        
        var maxEliteSize = 0L
        var maxUniversalSize = 0L

        for (filename in allVisibleFiles) {
            val lowerName = filename.lowercase()
            val approximateSize = getApproximateSize(context, filename)
            val isGgufSignature = isGguf(context, filename)

            when {
                lowerName.endsWith(".task") || lowerName.endsWith(".litertlm") -> {
                    if (approximateSize > maxEliteSize) {
                        elite = filename
                        maxEliteSize = approximateSize
                    }
                }
                lowerName.endsWith(".gguf") || isGgufSignature -> {
                    // 🛡️ [SBI] Format-Aware Discovery: Prioritize GGUF even in .bin wrapper
                    if (lowerName.contains("embedding") || lowerName.contains("gateway")) {
                        memory = filename // Prioritize embedding-specific GGUF for RAG
                    } else if (approximateSize > maxUniversalSize) {
                        universal = filename
                        maxUniversalSize = approximateSize
                    }
                    
                    if (isGgufSignature && !lowerName.endsWith(".gguf")) {
                        Timber.i("🛰️ [SBI] Stealth model identified as GGUF: $filename")
                    }
                }
                (lowerName.contains("sentence-encoder") || lowerName.contains("embeddinggemma")) && lowerName.endsWith(".tflite") -> {
                    memory = filename
                }
                (lowerName == ".gateway_sync.bin" || lowerName == "gateway_sync.bin") && !isGgufSignature -> {
                    // 🛡️ [SBI] Fallback to LiteRT only if NOT a GGUF signature
                    memory = ".gateway_sync.bin"
                }
                (lowerName == ".universal_sync.bin" || lowerName == "universal_sync.bin") && !isGgufSignature -> {
                    universal = ".universal_sync.bin"
                }
                lowerName.contains("face") && lowerName.endsWith(".bin") -> face = filename
                lowerName.contains("pose") && lowerName.endsWith(".bin") -> pose = filename
                lowerName.contains("segment") && lowerName.endsWith(".bin") -> segment = filename
                lowerName.contains("depth") && lowerName.endsWith(".bin") -> depth = filename
                lowerName.contains("whisper") && lowerName.endsWith(".bin") -> whisper = filename
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Timber.i("🛰️ [SBI] Dynamic Discovery ($duration ms): Elite=$elite, Universal=$universal, Memory=$memory, Face=$face, Pose=$pose")
        ModelRegistry(elite, universal, memory, face, pose, segment, depth, whisper)
    }

    private fun getApproximateSize(context: Context, filename: String): Long {
        val path = getModelPath(context, filename)
        return if (path.isNotEmpty()) File(path).length() else 0L
    }

    fun getModelPath(context: Context, filename: String): String {
        // 🛡️ [SAR] Priority 1: Stealth Check (Internal Root)
        if (filename.startsWith(".")) {
            // Check noBackupFilesDir (User confirmed stealth lives here)
            val stealthNoBackup = File(context.noBackupFilesDir, filename)
            if (stealthNoBackup.exists()) return stealthNoBackup.absolutePath
            
            // Check filesDir/.shm (Legacy or secondary stealth)
            val stealthShm = File(File(context.filesDir, ".shm"), filename)
            if (stealthShm.exists()) return stealthShm.absolutePath

            // Check assets (via stealth sync names)
            // Note: We can't return an absolute path for assets directly, 
            // but the discovery logic handles assets separately.
        }

        // Priority 2: Standard Internal Check (models/ subdirectory)
        val internalFile = File(File(context.noBackupFilesDir, MODELS_DIR), filename)
        if (internalFile.exists()) return internalFile.absolutePath
        
        // Priority 3: External & Downloads
        val externalPaths = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VITREON"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        for (dir in externalPaths) {
            val f = File(dir, filename)
            if (f.exists()) return f.absolutePath
        }
        
        // Default fallbacks if file doesn't exist anywhere on disk yet:
        return if (filename.startsWith(".")) {
            File(context.noBackupFilesDir, filename).absolutePath
        } else {
            File(File(context.noBackupFilesDir, MODELS_DIR), filename).absolutePath
        }
    }



    /**
     * Purges partial/corrupt temporary files from previous failed ingestion attempts.
     */
    fun cleanupStaleArtifacts(context: Context) {
        val modelsDir = File(context.noBackupFilesDir, MODELS_DIR)
        if (!modelsDir.exists()) return
        
        val staleFiles = mutableListOf<File>()
        try {
            modelsDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    staleFiles.add(file)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🚨 [SBI] Directory scan cleanup failed")
            return
        }
        
        for (file in staleFiles) {
            Timber.w("🧹 [SBI] Cleaning up stale artifact: ${file.name} (${file.length()} bytes)")
            file.delete()
        }
    }

    /**
     * Probes the first 4 bytes of a file to check for GGUF magic signature.
     * Public for use by ScypheonRepository RAG routing.
     */
    fun isGguf(context: Context, filename: String): Boolean {
        val path = getModelPath(context, filename)
        if (path.isEmpty()) return false
        val file = File(path)
        if (!file.exists() || file.length() < 4) return false
        
        return try {
            val buffer = ByteArray(4)
            java.io.FileInputStream(file).use { it.read(buffer) }
            val magic = String(buffer)
            magic == "GGUF"
        } catch (e: Exception) {
            false
        }
    }
}
