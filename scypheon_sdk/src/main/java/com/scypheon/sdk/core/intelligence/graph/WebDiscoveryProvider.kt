package com.scypheon.sdk.core.intelligence.graph

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WebDiscoveryProvider (HELIOS L4):
 * Provides extremely low-bandwidth external knowledge discovery.
 * Designed for "Last Resort" scenarios in low-connectivity areas.
 * 
 * Maps to Themes: Global Resilience & Future of Education.
 */
@Singleton
class WebDiscoveryProvider @Inject constructor(
    private val httpClient: OkHttpClient
) {

    /**
     * Fetches a clean, text-only summary from Wikipedia (Rest API).
     */
    suspend fun discoverWikipedia(query: String): String? = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
        executeCall(url)?.substringAfter("\"extract\":\"")?.substringBefore("\"")
    }

    /**
     * Fetches instant answers from DuckDuckGo.
     * Perfect for low-bandwidth general facts.
     */
    suspend fun discoverDuckDuckGo(query: String): String? = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.duckduckgo.com/?q=$encoded&format=json"
        executeCall(url)?.substringAfter("\"Abstract\":\"")?.substringBefore("\"")
    }

    /**
     * Fetches official drug safety data from OpenFDA.
     * Parses the raw JSON into a structured clinical schema.
     * Maps to Theme: Health & Sciences.
     */
    suspend fun discoverOpenFDA(drugName: String): String? = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(drugName, "UTF-8")
        val url = "https://api.fda.gov/drug/event.json?search=patient.drug.medicinalproduct:\"$encoded\"&limit=3"
        val rawJson = executeCall(url) ?: return@withContext null
        
        try {
            val root = org.json.JSONObject(rawJson)
            val results = root.optJSONArray("results") ?: return@withContext null
            
            val summary = java.lang.StringBuilder()
            summary.append("FDA Adverse Event Reports for $drugName:\n")
            
            for (i in 0 until Math.min(results.length(), 3)) {
                val event = results.getJSONObject(i)
                val isSerious = event.optString("serious", "2") == "1"
                val seriousFlag = if (isSerious) "[SERIOUS]" else "[NON-SERIOUS]"
                
                summary.append("- Event ID: ${event.optString("safetyreportid", "Unknown")} $seriousFlag\n")
                
                val patient = event.optJSONObject("patient")
                if (patient != null) {
                    val reactions = patient.optJSONArray("reaction")
                    if (reactions != null && reactions.length() > 0) {
                        val reactionList = mutableListOf<String>()
                        for (j in 0 until reactions.length()) {
                            reactionList.add(reactions.getJSONObject(j).optString("reactionmeddrapt", "Unknown"))
                        }
                        summary.append("  Reactions: ${reactionList.joinToString(", ")}\n")
                    }
                }
            }
            summary.toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse OpenFDA JSON for $drugName")
            null
        }
    }

    /**
     * Fetches content from Fandom community wikis.
     * Optimized for low-bandwidth text extraction.
     */
    suspend fun discoverFandom(wikiName: String, pageName: String): String? = withContext(Dispatchers.IO) {
        val safeWiki = wikiName.replace(Regex("[^a-zA-Z0-9-]"), "")
        val encodedPage = java.net.URLEncoder.encode(pageName, "UTF-8").replace("+", "%20")
        val url = "https://$safeWiki.fandom.com/api/v1/Articles/Details?ids=50&titles=$encodedPage"
        executeCall(url)?.substringAfter("\"abstract\":\"")?.substringBefore("\"")
    }

    private fun executeCall(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            Timber.w("🌐 [WEB_DISCOVERY] Call failed: $url")
            null
        }
    }
}
