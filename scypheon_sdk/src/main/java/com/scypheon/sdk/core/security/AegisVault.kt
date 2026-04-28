package com.scypheon.sdk.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.scypheon.sdk.core.model.ScypheonConfig

/**
 * AegisVault provides hardware-backed encrypted storage for sensitive credentials
 * like the Hugging Face API Token (HF_TOKEN). It utilizes the Android Keystore (TEE/SE)
 * to ensure tokens cannot be extracted from the device's storage.
 */
@Singleton
class AegisVault @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        VAULT_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Stores the Hugging Face API Token securely.
     */
    fun saveHfToken(token: String) {
        sharedPrefs.edit().putString(KEY_HF_TOKEN, token).apply()
    }

    /**
     * Retrieves the Hugging Face API Token. Returns null if not set.
     */
    fun getHfToken(): String? {
        return sharedPrefs.getString(KEY_HF_TOKEN, null)
    }

    /**
     * Stores the User's Display Name securely.
     */
    fun saveUserName(name: String) {
        sharedPrefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    /**
     * Retrieves the User's Display Name.
     */
    fun getUserName(): String? {
        return sharedPrefs.getString(KEY_USER_NAME, null)
    }

    /**
     * Stores the app version of the last hardware diagnostic check.
     */
    fun saveLastHwCheckVersion(version: Long) {
        sharedPrefs.edit().putLong(KEY_LAST_HW_VERSION, version).apply()
    }

    /**
     * Retrieves the app version of the last hardware diagnostic check.
     */
    fun getLastHwCheckVersion(): Long {
        return sharedPrefs.getLong(KEY_LAST_HW_VERSION, 0L)
    }

    /**
     * Stores the entire ScypheonConfig. 
     * Since these are non-sensitive tuning parameters, we store them as simple individual keys
     * to avoid unnecessary serialization overhead, but we keep them in the Aegis vault
     * for unified hardware-encrypted persistence.
     */
    fun saveConfig(config: ScypheonConfig) {
        sharedPrefs.edit().apply {
            putInt(KEY_MAX_TOKENS, config.maxTokens)
            putInt(KEY_CTX_WINDOW, config.contextWindow)
            putInt(KEY_TOP_K, config.topK)
            putFloat(KEY_TOP_P, config.topP)
            putFloat(KEY_TEMP, config.temperature)
            putInt(KEY_BACKEND, config.selectedBackendMode)
            putBoolean(KEY_THINKING, config.enableThinking)
        }.apply()
    }

    /**
     * Loads the ScypheonConfig from vault.
     */
    fun loadConfig(): ScypheonConfig {
        return ScypheonConfig(
            maxTokens = sharedPrefs.getInt(KEY_MAX_TOKENS, 2048),
            contextWindow = sharedPrefs.getInt(KEY_CTX_WINDOW, 4096),
            topK = sharedPrefs.getInt(KEY_TOP_K, 51),
            topP = sharedPrefs.getFloat(KEY_TOP_P, 0.95f),
            temperature = sharedPrefs.getFloat(KEY_TEMP, 0.8f),
            selectedBackendMode = sharedPrefs.getInt(KEY_BACKEND, 0),
            enableThinking = sharedPrefs.getBoolean(KEY_THINKING, true)
        )
    }

    /**
     * Clears all stored credentials from the vault.
     */
    fun clearVault() {
        sharedPrefs.edit().clear().apply()
    }

    companion object {
        private const val VAULT_NAME = "scypheon_aegis_vault"
        private const val KEY_HF_TOKEN = "hf_api_token"
        private const val KEY_USER_NAME = "user_display_name"
        private const val KEY_LAST_HW_VERSION = "last_hw_check_version"
        
        // AI Tuning Parameters
        private const val KEY_MAX_TOKENS = "cfg_max_tokens"
        private const val KEY_CTX_WINDOW = "cfg_ctx_window"
        private const val KEY_TOP_K = "cfg_top_k"
        private const val KEY_TOP_P = "cfg_top_p"
        private const val KEY_TEMP = "cfg_temp"
        private const val KEY_BACKEND = "cfg_backend"
        private const val KEY_THINKING = "cfg_thinking"
    }
}
