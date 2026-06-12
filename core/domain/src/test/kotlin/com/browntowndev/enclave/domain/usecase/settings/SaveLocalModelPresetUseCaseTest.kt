package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SaveLocalModelPresetUseCaseTest {
    @Test
    fun `invalid reasoning budget falls back to twenty five percent of context window`() =
        runTest {
            val fakeSave = CapturingSaveLocalModelConfigurationUseCase()
            val useCase = SaveLocalModelPresetUseCase(fakeSave)

            useCase(
                localModelId = LocalModelId("model"),
                draft =
                    LocalModelPresetDraft(
                        displayName = "Preset",
                        contextWindow = "8192",
                        reasoningBudget = "invalid",
                    ),
            ).getOrThrow()

            assertEquals(2048, fakeSave.saved?.reasoningBudget)
        }

    @Test
    fun `reasoning budget is clamped to context window`() =
        runTest {
            val fakeSave = CapturingSaveLocalModelConfigurationUseCase()
            val useCase = SaveLocalModelPresetUseCase(fakeSave)

            useCase(
                localModelId = LocalModelId("model"),
                draft =
                    LocalModelPresetDraft(
                        displayName = "Preset",
                        contextWindow = "4096",
                        reasoningBudget = "9000",
                    ),
            ).getOrThrow()

            assertEquals(4096, fakeSave.saved?.reasoningBudget)
        }
}

private class CapturingSaveLocalModelConfigurationUseCase : SaveLocalModelConfigurationUseCase {
    var saved: LocalModelConfiguration? = null

    override suspend fun invoke(configuration: LocalModelConfiguration): Result<LocalModelConfigurationId> {
        saved = configuration
        return Result.success(LocalModelConfigurationId("saved"))
    }
}
