package com.scypheon.sdk.core.provision

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ModelMetadata data class — validates field correctness.
 */
class ModelMetadataTest {

    @Test
    fun `default values are sane`() {
        val meta = ModelMetadata(
            id = "test-1",
            name = "Test Model",
            description = "A test model",
            fileName = "test.gguf",
            fileSizeMb = 500,
            downloadUrl = "https://huggingface.co/test/model/resolve/main/test.gguf",
            engineType = EngineType.GGUF
        )
        assertEquals("test-1", meta.id)
        assertEquals("Test Model", meta.name)
        assertEquals("test.gguf", meta.fileName)
        assertEquals(500, meta.fileSizeMb)
        assertEquals(EngineType.GGUF, meta.engineType)
    }

    @Test
    fun `copy preserves unchanged fields`() {
        val original = ModelMetadata(
            id = "test-1",
            name = "Test Model",
            description = "A test model",
            fileName = "test.gguf",
            fileSizeMb = 500,
            downloadUrl = "https://example.com",
            engineType = EngineType.GGUF,
            provider = "TestProvider",
            license = "Apache 2.0"
        )
        val copy = original.copy(name = "Updated Model")
        assertEquals("Updated Model", copy.name)
        assertEquals("test-1", copy.id)
        assertEquals("TestProvider", copy.provider)
        assertEquals("Apache 2.0", copy.license)
    }

    @Test
    fun `provider and license fields work`() {
        val meta = ModelMetadata(
            id = "gemma",
            name = "Gemma 4",
            description = "Google Gemma",
            fileName = "gemma.gguf",
            fileSizeMb = 1800,
            downloadUrl = "https://huggingface.co/test",
            engineType = EngineType.GGUF,
            provider = "Google",
            providerUrl = "https://huggingface.co/google/gemma-4",
            license = "Gemma Terms of Use",
            ramRequired = "4GB",
            contextLength = "128K"
        )
        assertEquals("Google", meta.provider)
        assertEquals("Gemma Terms of Use", meta.license)
        assertEquals("4GB", meta.ramRequired)
        assertEquals("128K", meta.contextLength)
    }

    @Test
    fun `EngineType enum values exist`() {
        assertNotNull(EngineType.GGUF)
        assertNotNull(EngineType.LITERT)
    }
}
