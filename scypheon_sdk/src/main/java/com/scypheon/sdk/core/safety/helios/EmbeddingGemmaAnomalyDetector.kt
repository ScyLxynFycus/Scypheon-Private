package com.scypheon.sdk.core.safety.helios

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock

/**
 * EmbeddingGemmaAnomalyDetector (HELIOS L0B):
 * Uses semantic embeddings to perform intent analysis.
 * Detects prompt injections and malicious pivots that bypass rule engines.
 */
@Singleton
class EmbeddingGemmaAnomalyDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val corpusProvider: ThreatCorpusProvider,
    private val embeddingCache: EmbeddingCache
) {

    private val initializationMutex = kotlinx.coroutines.sync.Mutex()
    private val embedMutex = kotlinx.coroutines.sync.Mutex()
    private var textEmbedder: TextEmbedder? = null
    
    @Volatile
    private var maliciousCorpus: List<FloatArray> = emptyList()
    private var isCorpusSeeded = false

    /**
     * Initializes the embedder on-demand. 
     * Must be called before first use, or will be called automatically by getEmbedding.
     */
    suspend fun initialize() = initializationMutex.withLock {
        if (textEmbedder != null && isCorpusSeeded) return@withLock
        
        withContext(Dispatchers.IO) {
            try {
                if (textEmbedder == null) {
                    Timber.i("🛡️ [HELIOS L0B] Initializing TextEmbedder on background thread...")
                    val modelPath = "models/embedding_gemma_300m.tflite"
                    val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelPath)
                    
                    val options = TextEmbedder.TextEmbedderOptions.builder()
                        .setBaseOptions(baseOptionsBuilder.build())
                        .setQuantize(true)
                        .build()
                    
                    textEmbedder = TextEmbedder.createFromOptions(context, options)
                    Timber.i("✅ [HELIOS L0B] TextEmbedder loaded successfully.")
                }
                
                if (!isCorpusSeeded) {
                    seedMaliciousCorpus()
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ [HELIOS L0B] Failed to load Embedding model or seed corpus")
            }
        }
    }

    private suspend fun getEmbedding(text: String): FloatArray? {
        // Hash for cache key
        val hash = hashString(text)
        
        // 1. Check cache first
        val cached = embeddingCache.get(hash)
        if (cached != null) return cached
        
        // 2. Generate if not in cache
        if (textEmbedder == null) {
            initialize()
        }
        
        return try {
            val result = embedMutex.withLock {
                textEmbedder?.embed(text)
            }
            val floatEmbedding = result?.embeddingResult()?.embeddings()?.firstOrNull()?.floatEmbedding()
            if (floatEmbedding != null) {
                embeddingCache.put(hash, floatEmbedding)
            }
            floatEmbedding
        } catch (e: Exception) {
            Timber.e(e, "Embedding generation failed")
            null
        }
    }
    
    private fun hashString(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    /**
     * Calculates the semantic risk of a prompt using Cosine Similarity.
     * Uses Sliding Window Chunking to prevent "Dilution Attacks".
     */
    suspend fun evaluateRisk(prompt: String): Float {
        val chunks = chunkText(prompt, windowSize = 50, overlap = 10)
        var maxRisk = 0f
        
        for (chunk in chunks) {
            val promptEmbedding = getEmbedding(chunk) ?: continue
            
            var chunkSimilarity = 0f
            for (maliciousVec in maliciousCorpus) {
                val sim = cosineSimilarity(promptEmbedding, maliciousVec)
                if (sim > chunkSimilarity) chunkSimilarity = sim
            }
            
            if (chunkSimilarity > maxRisk) {
                maxRisk = chunkSimilarity
            }
        }
        
        return maxRisk
    }

    private fun chunkText(text: String, windowSize: Int, overlap: Int): List<String> {
        val words = text.split("\\s+".toRegex())
        if (words.size <= windowSize) return listOf(text)
        
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val end = minOf(i + windowSize, words.size)
            chunks.add(words.subList(i, end).joinToString(" "))
            i += (windowSize - overlap)
        }
        return chunks
    }

    suspend fun calculateSimilarity(text1: String, text2: String): Float {
        val emb1 = getEmbedding(text1) ?: return 0f
        val emb2 = getEmbedding(text2) ?: return 0f
        return cosineSimilarity(emb1, emb2)
    }

    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        val size = minOf(vec1.size, vec2.size)
        for (i in 0 until size) {
            dotProduct += vec1[i] * vec2[i]
            normA += Math.pow(vec1[i].toDouble(), 2.0)
            normB += Math.pow(vec2[i].toDouble(), 2.0)
        }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    private suspend fun seedMaliciousCorpus() {
        val corpus = corpusProvider.loadCorpus()
        
        val embedder = textEmbedder ?: return
        val tempCorpus = mutableListOf<FloatArray>()
        
        for (prompt in corpus.entries) {
            try {
                // Try to hit cache first, otherwise embed
                val hash = hashString(prompt)
                var embedding = embeddingCache.get(hash)
                
                if (embedding == null) {
                    val result = embedMutex.withLock {
                        embedder.embed(prompt)
                    }
                    embedding = result?.embeddingResult()?.embeddings()?.firstOrNull()?.floatEmbedding()
                    if (embedding != null) {
                        embeddingCache.put(hash, embedding)
                    }
                }
                
                if (embedding != null) {
                    tempCorpus.add(embedding)
                }
            } catch (e: Exception) {
                Timber.w("🛡️ [HELIOS L0B] Failed to embed adversarial sample: ${prompt.take(30)}")
            }
        }
        maliciousCorpus = tempCorpus
        isCorpusSeeded = true
        Timber.i("🛡️ [HELIOS L0B] Seeded ${maliciousCorpus.size} adversarial embeddings (fromAsset=${corpus.isFromAsset}).")
    }
    
    fun close() {
        textEmbedder?.close()
        textEmbedder = null
        maliciousCorpus = emptyList()
        isCorpusSeeded = false
    }
}
