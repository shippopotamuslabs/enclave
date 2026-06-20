package com.browntowndev.enclave.feature.inference.repository

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenAiCompatibleModelCatalogSupportTest {
    @Test
    fun modelsListUrl_appendsV1ModelsWhenBaseHasNoPath() {
        assertEquals(
            "https://api.deepseek.com/v1/models",
            OpenAiCompatibleModelCatalogSupport.modelsListUrl("https://api.deepseek.com").toString(),
        )
    }

    @Test
    fun modelsListUrl_appendsModelsWhenBaseAlreadyHasVersionPath() {
        assertEquals(
            "https://api.z.ai/api/paas/v4/models",
            OpenAiCompatibleModelCatalogSupport.modelsListUrl("https://api.z.ai/api/paas/v4").toString(),
        )
    }

    @Test
    fun parseModels_toleratesMissingCreatedField() {
        val models =
            OpenAiCompatibleModelCatalogSupport.parseModels(
                """
                {
                  "data": [
                    {"id": "deepseek-chat", "object": "model"},
                    {"id": "deepseek-reasoner", "created": 42}
                  ]
                }
                """.trimIndent(),
                ApiProvider.DEEPSEEK,
            )

        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), models.map { it.id })
        assertEquals(null, models[0].created)
        assertEquals(42L, models[1].created)
    }
}
