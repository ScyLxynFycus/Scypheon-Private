package com.scypheon.app.provision

import com.scypheon.sdk.core.provision.EngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HuggingFaceClient — Live model search from HuggingFace Hub API.
 * 
 * [v1.5.2-SAR] Fetches public models on-demand, filtering for mobile-compatible
 * formats (GGUF, LiteRT). No data sent until user explicitly searches.
 * 
 * API endpoints used:
 *   - Search: GET /api/models?search={query}&sort=downloads&limit=20
 *   - Files:  GET /api/models/{id}/tree/main  
 *   - Info:   GET /api/models/{id}
 * 
 * Privacy: Only GET requests, no auth headers for public models, no user data sent.
 */
@Singleton
class HuggingFaceClient @Inject constructor() {

    companion object {
        private const val BASE_URL = "https://huggingface.co/api"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 15_000

        // Mobile-compatible file extensions
        private val MOBILE_EXTENSIONS = setOf(".gguf", ".task", ".litertlm")

        // Quantizations suitable for mobile (skip BF16, FP32, etc.)
        private val MOBILE_QUANTS = setOf(
            "Q2_K", "Q3_K_S", "Q3_K_M", "Q4_0", "Q4_1", "Q4_K_S", "Q4_K_M",
            "Q5_K_S", "Q5_K_M", "Q6_K", "Q8_0",
            "IQ2_M", "IQ3_XXS", "IQ4_XS", "IQ4_NL"
        )

        // Max file size for mobile: 10 GB
        private const val MAX_MOBILE_SIZE = 10_000_000_000L
    }

    // ═══════════════════════════════════════════════════════════════
    // Search
    // ═══════════════════════════════════════════════════════════════

    /**
     * Search HuggingFace for mobile-compatible models.
     * Filters for GGUF/LiteRT tagged repos, sorted by downloads.
     */
    suspend fun searchModels(query: String, limit: Int = 20): List<HfModelInfo> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/models?search=$encodedQuery&sort=downloads&direction=-1&limit=$limit"
            
            val json = httpGet(url) ?: return@withContext emptyList()
            val array = JSONArray(json)
            
            val results = mutableListOf<HfModelInfo>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val info = parseModelInfo(obj)
                
                // Filter: only repos with mobile-compatible tags & strictly whitelisted Gemma models
                val tags = info.tags
                val hasMobileFormat = tags.any { it == "gguf" || it.contains("litert") }
                val isGemma = info.repoId.contains("gemma", ignoreCase = true)
                if (hasMobileFormat && !info.isPrivate && isGemma) {
                    results.add(info)
                }
            }

            Timber.i("🔍 [HF] Search '$query' (Gemma Only) → ${results.size} results")
            results
        } catch (e: Exception) {
            Timber.e(e, "🔍 [HF] Search failed for '$query'")
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // File Listing
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetch the list of files in a model repo, filtered for mobile-compatible files.
     * Returns only .gguf, .task, .litertlm files under MAX_MOBILE_SIZE.
     */
    suspend fun fetchModelFiles(repoId: String): List<HfModelFile> = withContext(Dispatchers.IO) {
        if (!repoId.contains("gemma", ignoreCase = true)) {
            Timber.w("🔍 [HF] Blacklisted non-Gemma repository fetch blocked: $repoId")
            return@withContext emptyList()
        }
        try {
            val url = "$BASE_URL/models/$repoId/tree/main"
            val json = httpGet(url) ?: return@withContext emptyList()
            val array = JSONArray(json)
            
            val files = mutableListOf<HfModelFile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("type") != "file") continue
                
                val path = obj.getString("path")
                val extension = "." + path.substringAfterLast(".", "")
                
                if (extension !in MOBILE_EXTENSIONS) continue
                
                // Get real size from LFS or direct
                val size = if (obj.has("lfs")) {
                    obj.getJSONObject("lfs").getLong("size")
                } else {
                    obj.getLong("size")
                }
                
                // Skip files too large for mobile
                if (size > MAX_MOBILE_SIZE) continue
                
                // Extract quantization from filename
                val quant = extractQuantization(path)
                
                files.add(
                    HfModelFile(
                        fileName = path,
                        sizeBytes = size,
                        quantization = quant,
                        downloadUrl = "https://huggingface.co/$repoId/resolve/main/$path",
                        engineType = if (extension == ".gguf") EngineType.LLAMA_CPP else EngineType.LITE_RT
                    )
                )
            }

            // Sort by size ascending (smallest first)
            files.sortBy { it.sizeBytes }
            
            Timber.i("🔍 [HF] Files for '$repoId': ${files.size} mobile-compatible")
            files
        } catch (e: Exception) {
            Timber.e(e, "🔍 [HF] Failed to fetch files for '$repoId'")
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Model Detail
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetch detailed info about a model (license, description, etc.)
     */
    suspend fun fetchModelDetail(repoId: String): HfModelDetail? = withContext(Dispatchers.IO) {
        if (!repoId.contains("gemma", ignoreCase = true)) {
            Timber.w("🔍 [HF] Blacklisted non-Gemma details fetch blocked: $repoId")
            return@withContext null
        }
        try {
            val url = "$BASE_URL/models/$repoId"
            val json = httpGet(url) ?: return@withContext null
            val obj = JSONObject(json)
            
            val cardData = if (obj.has("cardData")) obj.getJSONObject("cardData") else null
            val license = cardData?.optString("license", "") ?: ""
            val licenseLink = cardData?.optString("license_link", "") ?: ""
            val lastModified = obj.optString("lastModified", "")

            HfModelDetail(
                repoId = repoId,
                license = license,
                licenseLink = licenseLink.ifBlank { "https://huggingface.co/$repoId" },
                isGated = obj.optBoolean("gated", false),
                downloads = obj.optInt("downloads", 0),
                likes = obj.optInt("likes", 0),
                lastModified = lastModified,
                modelCardUrl = "https://huggingface.co/$repoId"
            )
        } catch (e: Exception) {
            Timber.e(e, "🔍 [HF] Failed to fetch detail for '$repoId'")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HTTP
    // ═══════════════════════════════════════════════════════════════

    private fun httpGet(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/json")
                // No auth headers — public API only
            }

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                Timber.w("🔍 [HF] HTTP ${connection.responseCode} for $urlStr")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "🔍 [HF] HTTP error for $urlStr")
            null
        } finally {
            connection?.disconnect()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Parsing
    // ═══════════════════════════════════════════════════════════════

    private fun parseModelInfo(obj: JSONObject): HfModelInfo {
        val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
        val tags = (0 until tagsArray.length()).map { tagsArray.getString(it) }
        
        // Extract license from tags
        val license = tags.find { it.startsWith("license:") }?.removePrefix("license:") ?: ""
        
        return HfModelInfo(
            repoId = obj.getString("id"),
            author = obj.optString("author", obj.getString("id").substringBefore("/")),
            downloads = obj.optInt("downloads", 0),
            likes = obj.optInt("likes", 0),
            isPrivate = obj.optBoolean("private", false),
            isGated = false, // Will be fetched from detail
            tags = tags,
            license = license,
            pipelineTag = obj.optString("pipeline_tag", ""),
            createdAt = obj.optString("createdAt", "")
        )
    }

    private fun extractQuantization(fileName: String): String {
        val name = fileName.substringBeforeLast(".").uppercase()
        // Try to match common quantization patterns
        MOBILE_QUANTS.forEach { q ->
            if (name.contains(q.uppercase())) return q
        }
        // Check for UD variants
        if (name.contains("UD-")) {
            val afterUd = name.substringAfter("UD-")
            MOBILE_QUANTS.forEach { q ->
                if (afterUd.contains(q.uppercase().replace("_", ""))) return "UD-$q"
            }
        }
        // LiteRT models
        if (fileName.endsWith(".litertlm") || fileName.endsWith(".task")) return "int8"
        return "unknown"
    }

    // ═══════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════

    data class HfModelInfo(
        val repoId: String,         // e.g. "unsloth/gemma-4-E2B-it-GGUF"
        val author: String,         // e.g. "unsloth"
        val downloads: Int,
        val likes: Int,
        val isPrivate: Boolean,
        val isGated: Boolean,
        val tags: List<String>,
        val license: String,        // e.g. "apache-2.0"
        val pipelineTag: String,    // e.g. "text-generation"
        val createdAt: String
    ) {
        val displayName: String get() = repoId.substringAfter("/")
    }

    data class HfModelFile(
        val fileName: String,
        val sizeBytes: Long,
        val quantization: String,
        val downloadUrl: String,
        val engineType: EngineType
    ) {
        val sizeGb: Double get() = sizeBytes / 1_000_000_000.0
        val displaySize: String get() = if (sizeGb >= 1) "%.1f GB".format(sizeGb) else "%.0f MB".format(sizeBytes / 1_000_000.0)
    }

    data class HfModelDetail(
        val repoId: String,
        val license: String,        // e.g. "apache-2.0", "gemma"
        val licenseLink: String,    // URL to license page
        val isGated: Boolean,
        val downloads: Int,
        val likes: Int,
        val lastModified: String,
        val modelCardUrl: String    // URL to full model card
    ) {
        val licenseName: String get() = when (license.lowercase()) {
            "apache-2.0" -> "Apache 2.0"
            "mit" -> "MIT License"
            "gemma" -> "Gemma Terms of Use"
            "llama3.1" -> "Llama 3.1 Community License"
            "cc-by-4.0" -> "Creative Commons BY 4.0"
            else -> license.ifBlank { "See model card" }
        }
    }
}
