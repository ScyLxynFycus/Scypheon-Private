package com.scypheon.sdk.core.humanitarian

import android.content.Context
import android.speech.tts.TextToSpeech
import android.os.Vibrator
import android.os.VibrationEffect
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.util.Locale
import com.scypheon.sdk.core.utils.LocaleHelper
import com.scypheon.sdk.core.humanitarian.MedicalSpeechProvider
import com.scypheon.sdk.core.humanitarian.MedicalHapticProvider

@Module
@InstallIn(SingletonComponent::class)
object MedicalModule {

    @Provides
    @Singleton
    fun provideMedicalSpeechProvider(@ApplicationContext context: Context): MedicalSpeechProvider {
        return object : MedicalSpeechProvider {
            private var tts: TextToSpeech? = null
            init {
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = com.scypheon.sdk.core.utils.LocaleHelper.getLocalizedTtsLocale(context)
                    }
                }
            }
            override fun speak(text: String) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "medical_alert")
            }
            override fun shutdown() {
                tts?.shutdown()
            }
        }
    }

    @Provides
    @Singleton
    fun provideMedicalHapticProvider(@ApplicationContext context: Context): MedicalHapticProvider {
        return object : MedicalHapticProvider {
            @Suppress("DEPRECATION")
            private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            override fun vibrateSos() {
                vibrator?.let { vib ->
                    val pattern = longArrayOf(0, 100, 100, 100, 100, 100, 300, 300, 100, 300, 100, 300, 300, 100, 100, 100, 100, 100, 0)
                    vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
                }
            }
        }
    }
}
