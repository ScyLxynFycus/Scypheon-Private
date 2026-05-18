package com.scypheon.sdk.core.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRegistry @Inject constructor(
    private val context: Context
) {
    private val cacheFile = File(context.filesDir, "model_inventory.json")
    private val gson = Gson()

    data class ModelCandidate(
        val path: String, 
        val name: String, 
        val sizeMb: Long, 
        val quant: String, 
        val arch: String
    )

    suspend fun getAvailableModels(forceRefresh: Boolean = false): List<ModelCandidate> = withContext(Dispatchers.IO) {
        // Atomic File Read: 24h cache TTL
        if (cacheFile.exists() && !forceRefresh && (System.currentTimeMillis() - cacheFile.lastModified() < 86400000)) {
            try {
                val json = cacheFile.readText()
                val type = object : TypeToken<List<ModelCandidate>>() {}.type
                return@withContext gson.fromJson(json, type)
            } catch (e: Exception) {
                // Corrupt cache, proceed to scan
            }
        }

        val scanned = scanModelsDirectory()
        
        // Atomic Write: Write to temp then rename
        try {
            val tempFile = File(cacheFile.absolutePath + ".tmp")
            tempFile.writeText(gson.toJson(scanned))
            tempFile.renameTo(cacheFile)
        } catch (e: Exception) {
            // Log failure
        }
        
        scanned
    }

    private fun scanModelsDirectory(): List<ModelCandidate> {
        val modelList = mutableListOf<ModelCandidate>()
        val dir = File(context.filesDir, "models")
        if (dir.exists() && dir.isDirectory) {
            try {
                java.nio.file.Files.newDirectoryStream(dir.toPath()).use { stream ->
                    stream.forEach { path ->
                        val file = path.toFile()
                        if (file.name.endsWith(".gguf")) {
                            val parts = file.name.lowercase().split("-", "_", ".")
                            val quant = parts.find { it.startsWith("q") } ?: "unknown"
                            val arch = parts.find { it in listOf("gemma", "llama", "phi") } ?: "unknown"
                            modelList.add(ModelCandidate(file.absolutePath, file.name, file.length() / 1048576, quant, arch))
                        }
                    }
                }
            } catch (e: Exception) {
                // Log failure
            }
        }
        return modelList.sortedByDescending { it.sizeMb }
    }
}
