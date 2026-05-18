package com.scypheon.sdk.core.humanitarian.medical

import com.scypheon.sdk.core.memory.IVectorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicalVectorStore @Inject constructor(
    private val dao: PharmacopeiaDao,
    private val vectorEngine: IVectorEngine
) {
    /**
     * Index a list of medicines for semantic search.
     */
    suspend fun indexMedicines(drugs: List<PharmacopeiaEntry>) = withContext(Dispatchers.IO) {
        Timber.i("🧠 Semantic Indexing ${drugs.size} drugs...")
        val vectors = drugs.mapNotNull { drug ->
            val textToIndex = "${drug.drugName} ${drug.genericName} ${drug.dosage} ${drug.indications}"
            val embedding = vectorEngine.embedText(textToIndex)
            embedding?.let { emb ->
                MedicalVectorEntity(
                    sourceId = drug.id,
                    sourceType = "DRUG",
                    embedding = MedicalTypeConverters().fromFloatArray(emb)
                )
            }
        }
        dao.insertVectors(vectors)
    }

    /**
     * Index a list of first aid protocols for semantic search.
     */
    suspend fun indexProtocols(protocols: List<FirstAidEntity>) = withContext(Dispatchers.IO) {
        Timber.i("🧠 Semantic Indexing ${protocols.size} protocols...")
        val vectors = protocols.mapNotNull { protocol ->
            val textToIndex = "${protocol.conditionName} ${protocol.localSearchKeywords} ${protocol.instructionsEn}"
            val embedding = vectorEngine.embedText(textToIndex)
            embedding?.let { emb ->
                MedicalVectorEntity(
                    sourceId = protocol.id.toString(),
                    sourceType = "PROTOCOL",
                    embedding = MedicalTypeConverters().fromFloatArray(emb)
                )
            }
        }
        dao.insertVectors(vectors)
    }

    /**
     * Search for the most semantically similar medical entity.
     */
    suspend fun searchSimilar(query: String, sourceType: String, limit: Int = 1): List<String> = withContext(Dispatchers.IO) {
        val queryVector = try { vectorEngine.embedText(query) } catch (e: Exception) { null } 
            ?: return@withContext emptyList()
            
        val allVectors = dao.getAllVectors(sourceType)
        
        val converter = MedicalTypeConverters()
        val results = allVectors.map { entity ->
            val entityVector = converter.toFloatArray(entity.embedding)
            // Use a local helper or ensure IVectorEngine has calculateCosineSimilarity
            val similarity = cosineSimilarity(queryVector, entityVector)
            entity.sourceId to similarity
        }.filter { pair -> pair.second > 0.75f }
         .sortedByDescending { pair -> pair.second }
         .take(limit)
         .map { pair -> pair.first }

        if (results.isNotEmpty()) {
            Timber.i("🧠 Semantic Match Found for '$query' -> $results")
        }
        results
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
        return (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))).toFloat()
    }
}
