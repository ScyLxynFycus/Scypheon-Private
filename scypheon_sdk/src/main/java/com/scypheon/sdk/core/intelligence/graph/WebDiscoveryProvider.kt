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
        val url = "https://en.wikipedia.org/api/rest_v1/page/summary/${query.replace(" ", "_")}"
        executeCall(url)?.substringAfter("\"extract\":\"")?.substringBefore("\"")
    }

    /**
     * Fetches instant answers from DuckDuckGo.
     * Perfect for low-bandwidth general facts.
     */
    suspend fun discoverDuckDuckGo(query: String): String? = withContext(Dispatchers.IO) {
        val url = "https://api.duckduckgo.com/?q=${query.replace(" ", "+")}&format=json"
        executeCall(url)?.substringAfter("\"Abstract\":\"")?.substringBefore("\"")
    }

    /**
     * Fetches official drug safety data from OpenFDA.
     * Maps to Theme: Health & Sciences.
     */
    suspend fun discoverOpenFDA(drugName: String): String? = withContext(Dispatchers.IO) {
        val url = "https://api.fda.gov/drug/event.json?search=patient.drug.medicinalproduct:$drugName&limit=1"
        executeCall(url)?.take(500) // Returns raw event summary for grounding
    }

    /**
     * Fetches content from Fandom community wikis.
     * Optimized for low-bandwidth text extraction.
     */
    suspend fun discoverFandom(wikiName: String, pageName: String): String? = withContext(Dispatchers.IO) {
        val url = "https://$wikiName.fandom.com/api/v1/Articles/Details?ids=50&titles=${pageName.replace(" ", "_")}"
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
