package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val shareMediaPort = mockk<ShareMediaPort>(relaxed = true)
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val generationCoordinator = StudioGenerationCoordinator(studioRepository, mockk(relaxed = true))
    private val sessionMediaFlow = MutableStateFlow<List<com.browntowndev.enclave.feature.studio.StudioMediaUi>>(emptyList())
    private lateinit var promptCoordinator: StudioPromptCoordinator
    private lateinit var coordinator: StudioMediaActionsCoordinator

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())
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
            coEvery { studioRepository.getMediaById("perm-1") } returns
                StudioMediaAsset(
                    id = "perm-1",
                    localUri = persistedUri,
                    prompt = "test",
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = "album-1",
                )

            coordinator.shareSingleMedia("perm-1")
            advanceUntilIdle()

            verify { shareMediaPort.shareMedia(listOf(persistedUri), "image/*") }
        }

    @Test
    fun onEditMedia_fromUserAction_setsReferenceImageOnPromptCoordinator() =
        runTest {
            val persistedUri = "file:///data/user/0/com.app/files/permanent.jpg"
            coEvery { studioRepository.getMediaById("perm-1") } returns
                StudioMediaAsset(
                    id = "perm-1",
                    localUri = persistedUri,
                    prompt = "test",
                    mediaType = "IMAGE",
                    createdAt = 1L,
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
