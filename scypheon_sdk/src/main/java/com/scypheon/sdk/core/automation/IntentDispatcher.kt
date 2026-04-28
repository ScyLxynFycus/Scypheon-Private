package com.scypheon.sdk.core.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

/**
 * Enterprise Sub-System: Intent Dispatcher.
 * Acts as the Tier-1 execution strategy for PuppetMaster. Before resorting to complex
 * UI Tree parsing or visual clicking, it attempts to directly launch standard Android
 * Intents (Deep Links) for known applications (e.g., WhatsApp, Maps, Settings).
 */
class IntentDispatcher(private val context: Context) {

    /**
     * Maps natural language intents to strict Android URI Deep Links.
     */
    fun dispatchDeepLink(appName: String, actionText: String? = null): Boolean {
        Timber.i("🔗 IntentDispatcher: Attempting to launch deep link for $appName")

        return try {
            val intent = when (appName.lowercase()) {
                "whatsapp" -> {
                    val uri = Uri.parse("whatsapp://send?text=${actionText ?: ""}")
                    Intent(Intent.ACTION_VIEW, uri)
                }
                "maps", "google maps" -> {
                    val uri = Uri.parse("geo:0,0?q=${actionText ?: ""}")
                    Intent(Intent.ACTION_VIEW, uri)
                }
                "settings" -> {
                    Intent(android.provider.Settings.ACTION_SETTINGS)
                }
                "browser", "chrome" -> {
                    val uri = Uri.parse(if (actionText?.startsWith("http") == true) actionText else "https://google.com/search?q=$actionText")
                    Intent(Intent.ACTION_VIEW, uri)
                }
                else -> null
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Timber.i("✅ IntentDispatcher: Successfully launched $appName")
                true
            } else {
                Timber.w("⚠️ IntentDispatcher: Unknown app alias $appName")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ IntentDispatcher: Failed to launch deep link")
            false
        }
    }
}
