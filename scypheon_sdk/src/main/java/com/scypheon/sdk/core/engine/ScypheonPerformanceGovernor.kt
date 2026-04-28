package com.scypheon.sdk.core.engine

import android.os.Process
import timber.log.Timber

/**
 * Edge Max Feature: Performance Governor.
 * Dynamically shifts Android thread priorities and strict mode policies during LLM inference
 * to maximize Performance Core (Big Core) allocation and minimize OS throttling.
 */
object ScypheonPerformanceGovernor {

    private val originalThreadPriority = ThreadLocal<Int>()

    /**
     * Boosts the current thread to maximum UI/Urgent priority.
     * Should be called right before `generateResponse()` inside a background coroutine.
     */
    fun boostThreadPriority() {
        try {
            originalThreadPriority.set(Process.getThreadPriority(Process.myTid()))
            // Push thread priority to maximum allowed for non-system apps (audio/display level)
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            Timber.v("🚀 PerformanceGovernor: Boosted TID ${Process.myTid()} to URGENT priority.")
        } catch (e: Exception) {
            Timber.e("Failed to boost thread priority")
        }
    }

    /**
     * Restores the thread to its normal background state to save battery.
     */
    fun restoreThreadPriority() {
        try {
            originalThreadPriority.get()?.let {
                Process.setThreadPriority(it)
                Timber.v("💤 PerformanceGovernor: Restored TID ${Process.myTid()} to default priority.")
            }
        } catch (e: Exception) {
            Timber.e("Failed to restore thread priority")
        } finally {
            originalThreadPriority.remove() // Prevent memory leaks
        }
    }

    /**
     * Wrapping function to execute a block of code with maximum priority.
     * Incorporates a Thermal Duty Cycle: It yields slightly if execution runs too long
     * to prevent Android OS Thermal Throttling from killing the app process.
     */
    inline fun <T> runWithBoost(block: () -> T): T {
        boostThreadPriority()
        val startTime = System.currentTimeMillis()

        return try {
            block()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            restoreThreadPriority()

            if (duration > 5000) {
                Timber.w("🔥 PerformanceGovernor: Inference took ${duration}ms. Yielding to OS to prevent Thermal Kill.")
                // Allow OS a breather after a heavy multi-second continuous core lock
                Thread.sleep(100)
            }
        }
    }
}
