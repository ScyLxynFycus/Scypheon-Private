package com.scypheon.sdk.core.humanitarian.medical

import com.scypheon.sdk.core.annotations.SafetyCritical
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
 * Enterprise Medical Data Pipeline:
 * Automates the ingestion of verified medical data from OpenFDA.
 * Wajib dijalankan HANYA saat ada koneksi jaringan stabil (Sync Background).
 */
@SafetyCritical
@Singleton
class OpenFdaIngestor @Inject constructor(
    private val dao: PharmacopeiaDao
) {
    private val BASE_URL = "https://api.fda.gov/drug/label.json"
    private val TIMEOUT_MS = 15000

    suspend fun ingestDrug(genericName: String) = withContext(Dispatchers.IO) {
        Timber.i("📥 [FDA_INGEST] Initiating secure pull for: $genericName")
        
        try {
            val encoded = URLEncoder.encode(genericName, "UTF-8")
            val url = "$BASE_URL?search=openfda.generic_name:$encoded&limit=1"
            
            val response = httpGet(url) ?: run {
                Timber.w("⚠️ [FDA_INGEST] Network unreachable or API timeout. Skipping.")
                return@withContext
            }
            
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return@withContext
            if (results.length() == 0) return@withContext
            
            val fdaData = results.getJSONObject(0)
            val openFda = fdaData.optJSONObject("openfda") ?: JSONObject()
            
            // 🛑 CRITICAL FIX: Mapping yang presisi sesuai skema FTS4 kita tanpa bloat memori
            val entry = PharmacopeiaEntry(
                id = openFda.optJSONArray("spl_id")?.optString(0) ?: "FDA-${System.currentTimeMillis()}",
                drugName = openFda.optJSONArray("brand_name")?.optString(0) ?: genericName,
                genericName = genericName,
                
                // Ekstraksi teks yang aman (mengambil paragraf pertama saja jika terlalu panjang)
                dosage = extractCleanText(fdaData, "dosage_and_administration", "Consult clinician for precise dosage."),
                indications = extractCleanText(fdaData, "indications_and_usage", "General off-label / off-grid use."),
                contraindications = extractCleanText(fdaData, "contraindications", "Standard precautions apply."),
                
                // Variabel perhitungan klinis (Dibiarkan null karena FDA JSON tidak memiliki format angka baku)
                maxMgPerKg = null,
                maxDailyMg = null,
                maxSingleDoseMg = null,
                
                isHighRisk = detectRisk(fdaData),
                riskCategory = assignRiskCategory(fdaData),
                source = "api.fda.gov (Official)",
                lastUpdated = System.currentTimeMillis(),
                
                // Metadata
                atcCode = openFda.optJSONArray("pharm_class_cs")?.optString(0), // Mendekati kelas terapi
                route = openFda.optJSONArray("route")?.optString(0)?.uppercase(),
                storageConditions = extractCleanText(fdaData, "storage_and_handling", "Store in cool, dry place."),
                pregnancyCategory = extractCleanText(fdaData, "pregnancy", "Consult physician.")
            )
            
            // Insert or Ignore/Replace
            dao.insert(entry) 
            Timber.i("✅ [FDA_INGEST] Stored ${entry.drugName} | Route: ${entry.route} | High-Risk: ${entry.isHighRisk}")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ [FDA_INGEST] Pipeline fault for $genericName")
        }
    }

    /**
     * Membantu membersihkan array teks dari FDA agar tidak membebani database SQLite
     */
    private fun extractCleanText(json: JSONObject, key: String, fallback: String): String {
        val array = json.optJSONArray(key) ?: return fallback
        if (array.length() == 0) return fallback
        
        val text = array.optString(0)
        // Potong jika terlalu panjang agar FTS4 tidak tersedak (> 2000 karakter)
        return if (text.length > 2000) text.substring(0, 1997) + "..." else text
    }

    private fun detectRisk(json: JSONObject): Boolean {
        val warnings = json.optJSONArray("warnings")?.toString()?.lowercase() ?: ""
        val boxed = json.optJSONArray("boxed_warning")?.toString()?.lowercase() ?: ""
        
        // Cek Boxed Warning (FDA Black Box) - Ini sangat akurat
        if (boxed.isNotEmpty()) return true
        
        // Pengecekan heuristik biasa
        return warnings.contains("fatal") || warnings.contains("serious adverse") || warnings.contains("anaphylaxis")
    }

    private fun assignRiskCategory(json: JSONObject): String {
        val use = json.optJSONArray("indications_and_usage")?.toString()?.lowercase() ?: ""
        return when {
            use.contains("cardiac") || use.contains("heart") -> "CARDIAC"
            use.contains("respiratory") || use.contains("asthma") -> "RESPIRATORY"
            use.contains("pediatric") || use.contains("children") -> "PEDIATRIC"
            use.contains("infection") || use.contains("bacteria") -> "ANTI-INFECTIVE"
            else -> "GENERAL"
        }
    }

    private fun httpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "ScypheonSDK/Enterprise-Sync")
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
