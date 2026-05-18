package com.scypheon.sdk.core.humanitarian

interface MedicalSpeechProvider {
    fun speak(text: String)
    fun shutdown()
}
