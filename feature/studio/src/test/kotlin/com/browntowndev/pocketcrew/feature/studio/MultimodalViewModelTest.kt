package com.browntowndev.pocketcrew.feature.studio

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.media.GetProviderCapabilitiesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
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
    private val logger = mockk<LoggingPort>(relaxed = true)
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
            getProviderCapabilitiesUseCase,
            logger,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun `capabilities should be resolved from provider type instead of provider id`() = runTest {
        val mediaProviderId = MediaProviderId("custom-provider-id")
        val assignment = DefaultModelAssignment(
            modelType = ModelType.IMAGE_GENERATION,
            mediaProviderId = mediaProviderId,
        )
        val providerAsset = MediaProviderAsset(
            id = mediaProviderId,
            displayName = "Custom xAI Image Provider",
            provider = ApiProvider.XAI,
            capability = MediaCapability.IMAGE,
            credentialAlias = "xai-alias",
        )
        val xaiCapabilities = ProviderCapabilities(
            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
            supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
            supportedVideoQualities = emptyList(),
            supportedVideoResolutions = emptyList(),
            supportedVideoDurations = emptyList(),
            supportsReferenceImage = true,
            supportsVideo = false,
        )
        every { defaultModelRepository.observeDefaults() } returns flowOf(listOf(assignment))
        every { mediaProviderRepository.getMediaProviders() } returns flowOf(listOf(providerAsset))
        every { getProviderCapabilitiesUseCase(ApiProvider.XAI.name) } returns xaiCapabilities

        viewModel = MultimodalViewModel(
            imageGenerationPort,
            videoGenerationPort,
            studioRepository,
            defaultModelRepository,
            mediaProviderRepository,
            getProviderCapabilitiesUseCase,
            logger,
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(
            listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
            viewModel.uiState.value.capabilities?.supportedImageQualities,
        )
        verify { getProviderCapabilitiesUseCase(ApiProvider.XAI.name) }
        job.cancel()
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

    @Test
    fun `generate exposes moderation failure message for snackbar`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.failure(
            IllegalStateException("Prompt rejected due to moderation.")
        )

        viewModel.onPromptChange("rejected prompt")
        viewModel.generate()

        assertEquals("Prompt rejected due to moderation.", viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `generate propagates generation count and saves each returned image`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(
            listOf("first".toByteArray(), "second".toByteArray()),
        )

        viewModel.onPromptChange("batch prompt")
        viewModel.onUpdateSettings(ImageGenerationSettings(generationCount = 2))
        viewModel.generate()

        coVerify {
            imageGenerationPort.generateImage(
                prompt = "batch prompt",
                provider = any(),
                settings = ImageGenerationSettings(generationCount = 2),
            )
            studioRepository.saveMedia(
                match<ByteArray> { it.contentEquals("first".toByteArray()) },
                "batch prompt",
                MediaCapability.IMAGE.name,
            )
            studioRepository.saveMedia(
                match<ByteArray> { it.contentEquals("second".toByteArray()) },
                "batch prompt",
                MediaCapability.IMAGE.name,
            )
        }
        job.cancel()
    }

    @Test
    fun `scroll threshold only generates after prompt is sent and stops after stop action`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(
            listOf("image".toByteArray()),
        )

        viewModel.onPromptChange("scroll prompt")
        viewModel.onContinualModeToggle(true)
        viewModel.onGenerativeScrollThresholdVisible("anchor-1")

        coVerify(exactly = 0) { imageGenerationPort.generateImage(any(), any(), any()) }

        viewModel.generate()
        assertEquals(true, viewModel.uiState.value.isContinualGenerationActive)

        viewModel.onGenerativeScrollThresholdVisible("anchor-1")
        viewModel.onGenerativeScrollThresholdVisible("anchor-1")

        coVerify(exactly = 2) { imageGenerationPort.generateImage(any(), any(), any()) }

        viewModel.generate()
        assertEquals(false, viewModel.uiState.value.isContinualGenerationActive)

        viewModel.onGenerativeScrollThresholdVisible("anchor-2")

        coVerify(exactly = 2) { imageGenerationPort.generateImage(any(), any(), any()) }
        job.cancel()
    }

    @Test
    fun `prompt change disarms active continual generation`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(
            listOf("image".toByteArray()),
        )

        viewModel.onPromptChange("first prompt")
        viewModel.onContinualModeToggle(true)
        viewModel.generate()

        assertEquals(true, viewModel.uiState.value.isContinualGenerationActive)

        viewModel.onPromptChange("second prompt")
        viewModel.onGenerativeScrollThresholdVisible("anchor-1")

        assertEquals(false, viewModel.uiState.value.isContinualGenerationActive)
        coVerify(exactly = 1) { imageGenerationPort.generateImage(any(), any(), any()) }
        job.cancel()
    }

    @Test
    fun `ui state maps repository media to presentation media`() = runTest {
        every { studioRepository.observeAllMedia() } returns flowOf(
            listOf(
                StudioMediaAsset(
                    id = "asset-1",
                    localUri = "content://asset",
                    prompt = "prompt",
                    mediaType = MediaCapability.IMAGE.name,
                    createdAt = 1L,
                ),
            ),
        )
        viewModel = MultimodalViewModel(
            imageGenerationPort,
            videoGenerationPort,
            studioRepository,
            defaultModelRepository,
            mediaProviderRepository,
            getProviderCapabilitiesUseCase,
            logger,
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val media = viewModel.uiState.value.gallery.single()
        assertEquals(StudioMediaUi("asset-1", "content://asset", "prompt", MediaCapability.IMAGE, 1L), media)
        job.cancel()
    }
}
