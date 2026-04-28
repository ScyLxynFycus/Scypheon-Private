package com.scypheon.sdk.core.humanitarian.accessibility

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Enterprise Optimization: Audio Energy Monitor.
 * Calculates Root Mean Square (RMS) and Decibel (dB) levels from a raw PCM audio buffer.
 * Used as a low-power "wake word" gate to prevent ML models from running on complete silence,
 * saving immense battery life for always-on accessibility features.
 */
object AudioEnergyMonitor {

    // Threshold in dB above background noise required to wake the ML model
    // E.g., -50 dB is typical quiet room, -20 dB is loud speech/sirens
    private const val WAKE_THRESHOLD_DB = -35.0

    /**
     * Evaluates a FloatArray audio buffer (from MediaPipe or AudioRecord).
     * Returns true if the audio energy exceeds the wake threshold.
     */
    fun shouldWakeModel(audioBuffer: FloatArray): Boolean {
        if (audioBuffer.isEmpty()) return false

        var sumSquares = 0.0
        for (sample in audioBuffer) {
            sumSquares += (sample * sample)
        }

        val rms = sqrt(sumSquares / audioBuffer.size)

        // Prevent log10(0) which is -Infinity
        if (rms <= 0.0) return false

        // Calculate Decibels relative to Full Scale (dBFS)
        val dbfs = 20.0 * log10(rms)

        return dbfs >= WAKE_THRESHOLD_DB
    }
}
