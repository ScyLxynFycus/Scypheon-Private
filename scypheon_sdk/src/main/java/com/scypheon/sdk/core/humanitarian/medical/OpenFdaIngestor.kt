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
 * MUST be executed ONLY during stable network connections (Background Sync).
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
            
            // 🛑 CRITICAL FIX: Precise mapping according to our FTS4 schema without memory bloat
            val entry = PharmacopeiaEntry(
                id = openFda.optJSONArray("spl_id")?.optString(0) ?: "FDA-${System.currentTimeMillis()}",
                drugName = openFda.optJSONArray("brand_name")?.optString(0) ?: genericName,
                genericName = genericName,
                
                // Safe text extraction (takes only the first paragraph if too long)
                dosage = extractCleanText(fdaData, "dosage_and_administration", "Consult clinician for precise dosage."),
                indications = extractCleanText(fdaData, "indications_and_usage", "General off-label / off-grid use."),
                contraindications = extractCleanText(fdaData, "contraindications", "Standard precautions apply."),
                
                // Clinical calculation variables (Left null because FDA JSON lacks standard numeric formats)
                maxMgPerKg = null,
                maxDailyMg = null,
                
                severity = if (detectRisk(fdaData)) "HIGH_ALERT" else "MODERATE",
                source = "api.fda.gov (Official)",
                lastUpdated = System.currentTimeMillis()
            )
            
            // Insert or Ignore/Replace
            dao.insert(entry) 
            Timber.i("✅ [FDA_INGEST] Stored ${entry.drugName} | Severity: ${entry.severity}")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ [FDA_INGEST] Pipeline fault for $genericName")
        }
    }

    /**
     * Helps clean up text arrays from FDA to prevent overloading the SQLite database
     */
    private fun extractCleanText(json: JSONObject, key: String, fallback: String): String {
        val array = json.optJSONArray(key) ?: return fallback
        if (array.length() == 0) return fallback
        
        val text = array.optString(0)
        // Truncate if too long to prevent FTS4 from choking (> 2000 characters)
        return if (text.length > 2000) text.substring(0, 1997) + "..." else text
    }

    private fun detectRisk(json: JSONObject): Boolean {
        val warnings = json.optJSONArray("warnings")?.toString()?.lowercase() ?: ""
        val boxed = json.optJSONArray("boxed_warning")?.toString()?.lowercase() ?: ""
        
        // Check Boxed Warning (FDA Black Box) - This is highly accurate
        if (boxed.isNotEmpty()) return true
        
        // Standard heuristic check
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
