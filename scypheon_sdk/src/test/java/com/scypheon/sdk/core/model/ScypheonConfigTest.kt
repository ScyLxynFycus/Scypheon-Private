package com.scypheon.sdk.core.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ScypheonConfig — validates defaults and copy semantics.
 */
class ScypheonConfigTest {

    @Test
    fun `default config has sane values`() {
        val config = ScypheonConfig()
        assertEquals(4096, config.maxTokens)
        assertEquals(4096, config.contextWindow)
        assertEquals(51, config.topK)
        assertEquals(0.95f, config.topP, 0.001f)
        assertEquals(0.8f, config.temperature, 0.001f)
        assertEquals(0, config.selectedBackendMode)
        assertTrue(config.enableThinking)
        assertTrue(config.enableOnlineSearch)
        assertTrue(config.performanceMode)
        assertTrue(config.enableZeroLatency)
    }

    @Test
    fun `enableThinking can be toggled off via copy`() {
        val config = ScypheonConfig()
        assertTrue(config.enableThinking)

        val updated = config.copy(enableThinking = false)
        assertFalse(updated.enableThinking)
        // Other fields unchanged
        assertEquals(config.maxTokens, updated.maxTokens)
        assertEquals(config.temperature, updated.temperature, 0.001f)
    }

    @Test
    fun `enableOnlineSearch can be toggled off via copy`() {
        val config = ScypheonConfig()
        assertTrue(config.enableOnlineSearch)

        val updated = config.copy(enableOnlineSearch = false)
        assertFalse(updated.enableOnlineSearch)
    }

    @Test
    fun `backend modes 0 through 3 are accepted`() {
        for (mode in 0..3) {
            val config = ScypheonConfig(selectedBackendMode = mode)
            assertEquals(mode, config.selectedBackendMode)
        }
    }

    @Test
    fun `config copy with multiple changes`() {
        val config = ScypheonConfig()
        val updated = config.copy(
            maxTokens = 8192,
            contextWindow = 8192,
            topK = 40,
            topP = 0.9f,
            temperature = 0.7f,
            selectedBackendMode = 2,
            enableThinking = false,
            enableOnlineSearch = false
        )
        assertEquals(8192, updated.maxTokens)
        assertEquals(8192, updated.contextWindow)
        assertEquals(40, updated.topK)
        assertEquals(0.9f, updated.topP, 0.001f)
        assertEquals(0.7f, updated.temperature, 0.001f)
        assertEquals(2, updated.selectedBackendMode)
        assertFalse(updated.enableThinking)
        assertFalse(updated.enableOnlineSearch)
    }

    @Test
    fun `localModels defaults to empty`() {
        val config = ScypheonConfig()
        assertTrue(config.localModels.isEmpty())
    }

    @Test
    fun `backendDiagnostics defaults to empty`() {
        val config = ScypheonConfig()
        assertTrue(config.backendDiagnostics.isEmpty())
    }
}
