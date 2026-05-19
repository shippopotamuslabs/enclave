package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AutoAssignLocalModelsUseCaseTest {
    private val getLocalModelAssetsUseCase = mockk<GetLocalModelAssetsUseCase>()
    private val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
    private val setDefaultModelUseCase = mockk<SetDefaultModelUseCase>(relaxed = true)
    private val useCase =
        AutoAssignLocalModelsUseCase(
            getLocalModelAssetsUseCase,
            getDefaultModelsUseCase,
            setDefaultModelUseCase,
        )

    @Test
    fun `invoke only assigns to empty slots and preserves existing local assignments`() =
        runTest {
            val configId = LocalModelConfigurationId("config-1")
            val localModelId = LocalModelId("model-1")

            val config =
                LocalModelConfiguration(
                    id = configId,
                    localModelId = localModelId,
                    displayName = "Test Config",
                    maxTokens = 100,
                    contextWindow = 100,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.1,
                    systemPrompt = "",
                )

            val asset =
                LocalModelAsset(
                    metadata = mockk<com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata>(),
                    configurations = listOf(config),
                )

            // Mock existing assignment for MAIN slot
            val existingAssignment =
                mockk<com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment> {
                    every { modelType } returns ModelType.MAIN
                }

            coEvery { getDefaultModelsUseCase() } returns flowOf(listOf(existingAssignment))
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(listOf(asset))

            useCase()

            // Verify that it assigned to all text-based slots EXCEPT MAIN
            val expectedSlots = ModelType.entries.filter { it.isTextModel && it != ModelType.MAIN }
            expectedSlots.forEach { slot ->
                coVerify {
                    setDefaultModelUseCase(
                        modelType = slot,
                        localConfigId = configId,
                        apiConfigId = null,
                        ttsProviderId = null,
                        mediaProviderId = null,
                    )
                }
            }

            // Verify MAIN was NOT reassigned
            coVerify(exactly = 0) {
                setDefaultModelUseCase(modelType = ModelType.MAIN, any(), any(), any(), any())
            }
        }

    @Test
    fun `invoke does nothing if no local models exist`() =
        runTest {
            coEvery { getDefaultModelsUseCase() } returns
                flowOf(emptyList<com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment>())
            coEvery { getLocalModelAssetsUseCase() } returns flowOf(emptyList())

            useCase()

            coVerify(exactly = 0) { setDefaultModelUseCase(any(), any(), any(), any(), any()) }
        }
}
