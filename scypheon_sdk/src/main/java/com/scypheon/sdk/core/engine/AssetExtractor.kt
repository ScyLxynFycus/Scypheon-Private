package com.scypheon.sdk.core.engine

import android.content.Context
import java.io.File

/**
 * AssetExtractor: Dynamic Model Discovery Engine.
 * Scans internal storage and assets to register available AI brains.
 */
object AssetExtractor {

    data class LocalModel(
        val name: String,
        val path: String,
        val sizeMb: Long,
        val isGguf: Boolean
    )

    fun discoverAvailableModels(context: Context): List<LocalModel> {
        val modelList = mutableListOf<LocalModel>()
        
        // 1. Scan Internal Storage (FilesDir/models)
        val modelDir = File(context.filesDir, "models")
        if (modelDir.exists() && modelDir.isDirectory) {
            modelDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".gguf") || file.name.endsWith(".tflite"))) {
                    modelList.add(LocalModel(
                        name = file.name,
                        path = file.absolutePath,
                        sizeMb = file.length() / (1024 * 1024),
                        isGguf = file.name.endsWith(".gguf")
                    ))
                }
            }
        }

        // 2. Scan Assets (Fallback)
        context.assets.list("models")?.forEach { assetName ->
            if (assetName.endsWith(".gguf") || assetName.endsWith(".tflite")) {
                modelList.add(LocalModel(
                    name = assetName,
                    path = "assets://models/$assetName",
                    sizeMb = 2000, // Approximate for assets as we can't get exact size easily
                    isGguf = assetName.endsWith(".gguf")
                ))
            }
        }

        return modelList.distinctBy { it.name }
    }
}
