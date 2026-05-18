package com.scypheon.app.provision

import com.scypheon.sdk.core.provision.EngineType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HuggingFaceClient data classes and formatting logic.
 * These are pure unit tests — no network calls needed.
 */
class HuggingFaceClientTest {

    // ─── HfModelInfo Tests ───

    @Test
    fun `HfModelInfo displayName strips author prefix`() {
        val info = HuggingFaceClient.HfModelInfo(
            repoId = "unsloth/gemma-4-E2B-it-GGUF",
            author = "unsloth",
            downloads = 15000,
            likes = 100,
            isPrivate = false,
            isGated = false,
            tags = listOf("gguf", "gemma"),
            license = "apache-2.0",
            pipelineTag = "text-generation",
            createdAt = "2025-12-01"
        )
        assertEquals("gemma-4-E2B-it-GGUF", info.displayName)
    }

    @Test
    fun `HfModelInfo with no slash in repoId returns full name`() {
        val info = HuggingFaceClient.HfModelInfo(
            repoId = "standalone-model",
            author = "",
            downloads = 100,
            likes = 0,
            isPrivate = false,
            isGated = false,
            tags = emptyList(),
            license = "",
            pipelineTag = "",
            createdAt = ""
        )
        assertEquals("standalone-model", info.displayName)
    }

    // ─── HfModelFile Tests ───

    @Test
    fun `HfModelFile displaySize shows GB for large files`() {
        val file = HuggingFaceClient.HfModelFile(
            fileName = "model-Q4_K_M.gguf",
            sizeBytes = 1_800_000_000L,
            quantization = "Q4_K_M",
            downloadUrl = "https://huggingface.co/test/model/resolve/main/model-Q4_K_M.gguf",
            engineType = EngineType.GGUF
        )
        assertEquals("1.8 GB", file.displaySize)
    }

    @Test
    fun `HfModelFile displaySize shows MB for small files`() {
        val file = HuggingFaceClient.HfModelFile(
            fileName = "small.gguf",
            sizeBytes = 500_000_000L,
            quantization = "Q2_K",
            downloadUrl = "https://example.com",
            engineType = EngineType.GGUF
        )
        assertEquals("500 MB", file.displaySize)
    }

    @Test
    fun `HfModelFile sizeGb calculates correctly`() {
        val file = HuggingFaceClient.HfModelFile(
            fileName = "model.gguf",
            sizeBytes = 3_500_000_000L,
            quantization = "Q5_K_M",
            downloadUrl = "https://example.com",
            engineType = EngineType.GGUF
        )
        assertEquals(3.5, file.sizeGb, 0.01)
    }

    @Test
    fun `HfModelFile engineType is correct for GGUF`() {
        val file = HuggingFaceClient.HfModelFile(
            fileName = "model.gguf",
            sizeBytes = 1_000_000L,
            quantization = "Q4_K_M",
            downloadUrl = "https://example.com",
            engineType = EngineType.GGUF
        )
        assertEquals(EngineType.GGUF, file.engineType)
    }

    @Test
    fun `HfModelFile engineType is correct for LiteRT`() {
        val file = HuggingFaceClient.HfModelFile(
            fileName = "model.task",
            sizeBytes = 500_000_000L,
            quantization = "int8",
            downloadUrl = "https://example.com",
            engineType = EngineType.LITERT
        )
        assertEquals(EngineType.LITERT, file.engineType)
    }

    // ─── HfModelDetail License Tests ───

    @Test
    fun `licenseName maps Apache 2_0 correctly`() {
        val detail = createDetail("apache-2.0")
        assertEquals("Apache 2.0", detail.licenseName)
    }

    @Test
    fun `licenseName maps MIT correctly`() {
        val detail = createDetail("mit")
        assertEquals("MIT License", detail.licenseName)
    }

    @Test
    fun `licenseName maps Gemma correctly`() {
        val detail = createDetail("gemma")
        assertEquals("Gemma Terms of Use", detail.licenseName)
    }

    @Test
    fun `licenseName maps Llama 3_1 correctly`() {
        val detail = createDetail("llama3.1")
        assertEquals("Llama 3.1 Community License", detail.licenseName)
    }

    @Test
    fun `licenseName maps CC-BY-4_0 correctly`() {
        val detail = createDetail("cc-by-4.0")
        assertEquals("Creative Commons BY 4.0", detail.licenseName)
    }

    @Test
    fun `licenseName returns raw value for unknown license`() {
        val detail = createDetail("custom-license-v2")
        assertEquals("custom-license-v2", detail.licenseName)
    }

    @Test
    fun `licenseName returns See model card for blank license`() {
        val detail = createDetail("")
        assertEquals("See model card", detail.licenseName)
    }

    // ─── Helper ───

    private fun createDetail(license: String) = HuggingFaceClient.HfModelDetail(
        repoId = "test/model",
        license = license,
        licenseLink = "https://example.com/license",
        isGated = false,
        downloads = 1000,
        likes = 50,
        lastModified = "2025-01-01",
        modelCardUrl = "https://huggingface.co/test/model"
    )
}
