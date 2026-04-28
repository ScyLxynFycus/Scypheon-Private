package com.scypheon.sdk.core.utils

import android.content.Context
import java.util.Locale

object LocaleHelper {
    fun getCurrentLanguage(): String {
        return Locale.getDefault().language
    }

    fun getCurrentLanguageCode(context: Context? = null): String {
        return getCurrentLanguage()
    }

    fun getLocalizedTtsLocale(context: Context? = null): Locale {
        return Locale.getDefault()
    }
}
