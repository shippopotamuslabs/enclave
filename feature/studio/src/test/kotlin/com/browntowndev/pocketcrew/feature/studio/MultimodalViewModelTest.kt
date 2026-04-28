package com.browntowndev.pocketcrew.feature.studio

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.media.GetProviderCapabilitiesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModelTest {
    private val imageGenerationPort = mockk<ImageGenerationPort>(relaxed = true)
    private val videoGenerationPort = mockk<VideoGenerationPort>(relaxed = true)
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>(relaxed = true)
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>(relaxed = true)
    private val getProviderCapabilitiesUseCase = mockk<GetProviderCapabilitiesUseCase>()
    private lateinit var viewModel: MultimodalViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        
        val mediaProviderId = MediaProviderId("test-provider")
        val assignment = DefaultModelAssignment(
            modelType = ModelType.IMAGE_GENERATION,
            mediaProviderId = mediaProviderId
        )
        val providerAsset = MediaProviderAsset(
            id = mediaProviderId,
            displayName = "Test Provider",
            provider = ApiProvider.OPENAI,
            capability = MediaCapability.IMAGE,
            credentialAlias = "test-alias"
        )

        every { defaultModelRepository.observeDefaults() } returns flowOf(listOf(assignment))
        every { mediaProviderRepository.getMediaProviders() } returns flowOf(listOf(providerAsset))
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { getProviderCapabilitiesUseCase(any()) } returns ProviderCapabilities(
            supportedAspectRatios = AspectRatio.entries.toList(),
            supportedImageQualities = listOf(GenerationQuality.SPEED),
            supportedVideoQualities = listOf(GenerationQuality.SPEED),
            supportedVideoResolutions = listOf("480p"),
            supportedVideoDurations = listOf(5),
            supportsReferenceImage = true,
            supportsVideo = true
        )
        
        viewModel = MultimodalViewModel(
            imageGenerationPort,
            videoGenerationPort,
            studioRepository,
            defaultModelRepository,
            mediaProviderRepository,
            getProviderCapabilitiesUseCase
        )
    }

    @Test
    fun `capabilities should be updated via use case`() = runTest {
        // Given
        val capabilities = ProviderCapabilities(
            supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE),
            supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.HD),
            supportedVideoQualities = emptyList(),
            supportedVideoResolutions = emptyList(),
            supportedVideoDurations = emptyList(),
            supportsReferenceImage = false,
            supportsVideo = false
        )
        every { getProviderCapabilitiesUseCase(any()) } returns capabilities

        // Start collection to trigger stateIn(SharingStarted.WhileSubscribed)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE), state.capabilities?.supportedAspectRatios)
        assertEquals(listOf(GenerationQuality.SPEED, GenerationQuality.HD), state.capabilities?.supportedImageQualities)
    }

    @Test
    fun `selecting template should update selectedTemplateId`() = runTest {
        // Given
        val template = com.browntowndev.pocketcrew.domain.model.media.StudioTemplate(
            id = "test",
            name = "Test",
            exampleUri = "",
            promptPrefix = "Prefix ",
            promptSuffix = " Suffix"
        )

        // Start collection to trigger stateIn(SharingStarted.WhileSubscribed)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        // When
        viewModel.onTemplateSelected(template)
        
        // Then
        assertEquals("test", viewModel.uiState.value.selectedTemplateId)
    }
}
