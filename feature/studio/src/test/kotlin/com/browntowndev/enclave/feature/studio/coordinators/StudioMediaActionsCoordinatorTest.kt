package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.core.testing.FakeShareMediaPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.feature.studio.test.FakeStudioGenerationDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioMediaActionsCoordinatorTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val shareMediaPort = FakeShareMediaPort()
    private val studioRepository = FakeStudioRepository()
    private val generationCoordinator =
        StudioGenerationCoordinator(studioRepository, FakeStudioGenerationDispatcher())
    private val sessionMediaFlow = MutableStateFlow<List<com.browntowndev.enclave.feature.studio.StudioMediaUi>>(emptyList())
    private lateinit var promptCoordinator: StudioPromptCoordinator
    private lateinit var coordinator: StudioMediaActionsCoordinator

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        promptCoordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)
        coordinator = StudioMediaActionsCoordinator(shareMediaPort, studioRepository, generationCoordinator)
        coordinator.attach(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            sessionMedia = sessionMediaFlow,
            hasImageToVideoModel = flowOf(true),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shareSingleMedia_resolvesPersistedAssetFromRepository() =
        runTest {
            val persistedUri = "file:///data/user/0/com.app/files/permanent.jpg"
            studioRepository.emitMedia(
                listOf(
                    StudioMediaAsset(
                        id = "perm-1",
                        localUri = persistedUri,
                        prompt = "test",
                        mediaType = "IMAGE",
                        createdAt = 1L,
                        albumId = "album-1",
                    ),
                ),
            )

            coordinator.shareSingleMedia("perm-1")
            advanceUntilIdle()

            assertEquals(listOf(listOf(persistedUri) to "image/*"), shareMediaPort.shareMediaCalls)
        }

    @Test
    fun onEditMedia_fromUserAction_setsReferenceImageOnPromptCoordinator() =
        runTest {
            val persistedUri = "file:///data/user/0/com.app/files/permanent.jpg"
            studioRepository.emitMedia(
                listOf(
                    StudioMediaAsset(
                        id = "perm-1",
                        localUri = persistedUri,
                        prompt = "test",
                        mediaType = "IMAGE",
                        createdAt = 1L,
                    ),
                ),
            )

            coordinator.onEditMedia(
                prompt = promptCoordinator,
                assetId = "perm-1",
                fromUserAction = true,
            )
            advanceUntilIdle()

            val settings = promptCoordinator.settings.value as ImageGenerationSettings
            assertEquals(persistedUri, settings.referenceImageUri)
        }
}
