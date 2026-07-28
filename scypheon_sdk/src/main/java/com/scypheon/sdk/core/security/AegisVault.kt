package com.scypheon.sdk.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.scypheon.sdk.core.model.ScypheonConfig

/**
 * AegisVault: Enterprise hardware-backed encrypted storage for sensitive credentials.
 * Hardened to support isolated processes via software-backed key derivation fallback.
 */
@Singleton
class AegisVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: DatabaseKeyManager
) {
    private val isMainProcess by lazy {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val myPid = android.os.Process.myPid()
        activityManager.runningAppProcesses?.any { it.pid == myPid && !it.processName.contains(":") } ?: true
    }

    private val masterKey by lazy {
        val alias = keyManager.getOrCreateMasterKey()
        MasterKey.Builder(context, alias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPrefs by lazy {
        try {
            createSharedPrefs()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "AegisVault: EncryptedSharedPreferences initialization failed. Purging corrupted vault.")
            context.deleteSharedPreferences(VAULT_NAME)
            createSharedPrefs()
        }
    }

    private fun createSharedPrefs() = EncryptedSharedPreferences.create(
        context,
        VAULT_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveHfToken(token: String) = sharedPrefs.edit().putString(KEY_HF_TOKEN, token).apply()
    fun getHfToken(): String? = sharedPrefs.getString(KEY_HF_TOKEN, null)

    fun saveUserName(name: String) = sharedPrefs.edit().putString(KEY_USER_NAME, name).apply()
    fun getUserName(): String? = sharedPrefs.getString(KEY_USER_NAME, null)

    fun saveLastHwCheckVersion(version: Long) = sharedPrefs.edit().putLong(KEY_LAST_HW_VERSION, version).apply()
    fun getLastHwCheckVersion(): Long = sharedPrefs.getLong(KEY_LAST_HW_VERSION, 0L)

    fun saveConfig(config: ScypheonConfig) {
        sharedPrefs.edit().apply {
            putInt(KEY_MAX_TOKENS, config.maxTokens)
            putInt(KEY_CTX_WINDOW, config.contextWindow)
            putInt(KEY_TOP_K, config.topK)
            putFloat(KEY_TOP_P, config.topP)
            putFloat(KEY_TEMP, config.temperature)
            putInt(KEY_BACKEND, config.selectedBackendMode)
            putBoolean(KEY_THINKING, config.enableThinking)
            putBoolean(KEY_ONLINE_SEARCH, config.enableOnlineSearch)
            putString(KEY_THEME_MODE, config.themeMode.name)
            putString(KEY_CHAT_BUBBLE, config.chatBubbleStyle.name)
        }.apply()
    }

    fun loadConfig(): ScypheonConfig {
        return ScypheonConfig(
            maxTokens = sharedPrefs.getInt(KEY_MAX_TOKENS, 2048),
            contextWindow = sharedPrefs.getInt(KEY_CTX_WINDOW, 4096),
            topK = sharedPrefs.getInt(KEY_TOP_K, 51),
            topP = sharedPrefs.getFloat(KEY_TOP_P, 0.95f),
            temperature = sharedPrefs.getFloat(KEY_TEMP, 0.8f),
            selectedBackendMode = sharedPrefs.getInt(KEY_BACKEND, 0),
            enableThinking = sharedPrefs.getBoolean(KEY_THINKING, true),
            enableOnlineSearch = sharedPrefs.getBoolean(KEY_ONLINE_SEARCH, true),
            themeMode = try {
                com.scypheon.sdk.core.model.ThemeMode.valueOf(sharedPrefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM")
            } catch (e: Exception) {
                com.scypheon.sdk.core.model.ThemeMode.SYSTEM
            },
            chatBubbleStyle = try {
                com.scypheon.sdk.core.model.ChatBubbleStyle.valueOf(sharedPrefs.getString(KEY_CHAT_BUBBLE, "GRADIENT_BLUE") ?: "GRADIENT_BLUE")
            } catch (e: Exception) {
                com.scypheon.sdk.core.model.ChatBubbleStyle.GRADIENT_BLUE
            }
        )
    }

    fun clearVault() = sharedPrefs.edit().clear().apply()

    companion object {
        private const val VAULT_NAME = "scypheon_aegis_vault"
        private const val KEY_HF_TOKEN = "hf_api_token"
        private const val KEY_USER_NAME = "user_display_name"
        private const val KEY_LAST_HW_VERSION = "last_hw_check_version"
        private const val KEY_MAX_TOKENS = "cfg_max_tokens"
        private const val KEY_CTX_WINDOW = "cfg_ctx_window"
        private const val KEY_TOP_K = "cfg_top_k"
        private const val KEY_TOP_P = "cfg_top_p"
        private const val KEY_TEMP = "cfg_temp"
        private const val KEY_BACKEND = "cfg_backend"
        private const val KEY_THINKING = "cfg_thinking"
        private const val KEY_ONLINE_SEARCH = "cfg_online_search"
        private const val KEY_THEME_MODE = "cfg_theme_mode"
        private const val KEY_CHAT_BUBBLE = "cfg_chat_bubble"
    }
}
