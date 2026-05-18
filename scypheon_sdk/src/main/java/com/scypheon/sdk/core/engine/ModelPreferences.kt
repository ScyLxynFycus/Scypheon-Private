package com.scypheon.sdk.core.engine

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's model selection across app restarts.
 *
 * Uses SharedPreferences (not DataStore) for synchronous read at ViewModel init time.
 * The `prefs` property is lazy to avoid DiskReadViolation during Hilt constructor injection
 * on the main thread — consistent with [HardwarePreferences] pattern from Sprint 3.
 *
 * File name: "scypheon_model_prefs" (as specified in the Chief Architect directive).
 */
@Singleton
class ModelPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("scypheon_model_prefs", Context.MODE_PRIVATE)
    }

    fun saveSelectedModel(model: DetectedModel) {
        prefs.edit()
            .putString(KEY_ID,      model.id)
            .putString(KEY_NAME,    model.displayName)
            .putString(KEY_ENGINE,  model.engine.name)
            .putLong(KEY_SIZE,      model.sizeMb)
            .putString(KEY_PATH,    model.filePath)
            .apply()
        Timber.d("ModelPreferences: saved selection '${model.displayName}' (${model.engine.name})")
    }

    fun getSelectedModel(): DetectedModel? {
        val id     = prefs.getString(KEY_ID, null)     ?: return null
        val name   = prefs.getString(KEY_NAME, null)   ?: return null
        val engine = prefs.getString(KEY_ENGINE, null) ?: return null
        val size   = prefs.getLong(KEY_SIZE, 0L)
        val path   = prefs.getString(KEY_PATH, "")     ?: ""

        val engineType = runCatching { EngineType.valueOf(engine) }.getOrElse {
            Timber.w("ModelPreferences: unknown engine type '$engine', clearing selection")
            clearSelection()
            return null
        }

        return DetectedModel(id, name, engineType, size, path)
    }

    fun clearSelection() {
        prefs.edit().clear().apply()
        Timber.d("ModelPreferences: selection cleared")
    }

    private companion object {
        const val KEY_ID     = "model_id"
        const val KEY_NAME   = "model_display"
        const val KEY_ENGINE = "model_engine"
        const val KEY_SIZE   = "model_size_mb"
        const val KEY_PATH   = "model_path"
    }
}
