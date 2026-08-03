package com.scypheon.sdk.core.provision

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ModelHubSource — validates the curated model catalog.
 */
class ModelHubSourceTest {

    @Test
    fun `recommendedModels should not be empty`() {
        val models = ModelHubSource.recommendedModels
        assertTrue("Recommended models list must not be empty", models.isNotEmpty())
    }

    @Test
    fun `all recommended models have unique IDs`() {
        val models = ModelHubSource.recommendedModels
        val ids = models.map { it.id }
        assertEquals("Duplicate IDs found", ids.size, ids.toSet().size)
    }

    @Test
    fun `all recommended models have unique fileNames`() {
        val models = ModelHubSource.recommendedModels
        val names = models.map { it.fileName }
        assertEquals("Duplicate fileNames found", names.size, names.toSet().size)
    }

    @Test
    fun `all models have valid download URLs`() {
        val models = ModelHubSource.recommendedModels
        for (model in models) {
            assertTrue(
                "Model ${model.id} downloadUrl must start with https://huggingface.co/",
                model.downloadUrl.startsWith("https://huggingface.co/")
            )
            assertTrue(
                "Model ${model.id} downloadUrl must contain /resolve/main/",
                model.downloadUrl.contains("/resolve/main/")
            )
        }
    }

    @Test
    fun `all models have valid engine types`() {
        val validTypes = setOf(EngineType.GGUF, EngineType.LITERT)
        val models = ModelHubSource.recommendedModels
        for (model in models) {
            assertTrue(
                "Model ${model.id} has invalid engineType: ${model.engineType}",
                model.engineType in validTypes
            )
        }
    }

    @Test
    fun `GGUF models have gguf extension in fileName`() {
        val models = ModelHubSource.recommendedModels.filter { it.engineType == EngineType.GGUF }
        for (model in models) {
            assertTrue(
                "GGUF model ${model.id} fileName must end with .gguf",
                model.fileName.endsWith(".gguf")
            )
        }
    }

    @Test
    fun `LiteRT models have task extension in fileName`() {
        val models = ModelHubSource.recommendedModels.filter { it.engineType == EngineType.LITERT }
        for (model in models) {
            assertTrue(
                "LiteRT model ${model.id} fileName must end with .task",
                model.fileName.endsWith(".task")
            )
        }
    }

    @Test
    fun `all models have positive fileSizeMb`() {
        val models = ModelHubSource.recommendedModels
        for (model in models) {
            assertTrue(
                "Model ${model.id} fileSizeMb must be positive, was ${model.fileSizeMb}",
                model.fileSizeMb > 0
            )
        }
    }

    @Test
    fun `all models have non-empty required fields`() {
        val models = ModelHubSource.recommendedModels
        for (model in models) {
            assertTrue("Model ${model.id} name must not be blank", model.name.isNotBlank())
            assertTrue("Model ${model.id} description must not be blank", model.description.isNotBlank())
            assertTrue("Model ${model.id} provider must not be blank", model.provider.isNotBlank())
            assertTrue("Model ${model.id} license must not be blank", model.license.isNotBlank())
        }
    }

    @Test
    fun `no model exceeds 10GB size limit for mobile`() {
        val models = ModelHubSource.recommendedModels
        for (model in models) {
            assertTrue(
                "Model ${model.id} exceeds 10GB mobile limit: ${model.fileSizeMb}MB",
                model.fileSizeMb <= 10_000
            )
        }
    }
}
