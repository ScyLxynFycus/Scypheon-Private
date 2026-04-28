package com.scypheon.sdk.core.humanitarian

/**
 * Standard interface for all Scypheon Humanitarian Agents.
 * Enables on-demand resource management and lifecycle control.
 */
interface ScypheonAgent {
    /**
     * Performs lightweight pre-initialization (e.g., loading vocabulary, 
     * preparing buffers). Should be called before the agent is first used.
     */
    fun warmUp()

    /**
     * Releases heavy resources (TTS, STT, Native Models) to free RAM.
     */
    fun release()

    /**
     * Returns true if the agent's engines are initialized and ready for use.
     */
    fun isReady(): Boolean
}
