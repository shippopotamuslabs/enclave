package com.browntowndev.enclave.feature.studio

import app.cash.turbine.test
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.port.media.MediaSaverPort
import com.browntowndev.enclave.domain.usecase.media.SaveMediaToGalleryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class StudioDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val mediaSaverPort = mockk<MediaSaverPort>()
    private lateinit var viewModel: StudioDetailViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = StudioDetailViewModel(SaveMediaToGalleryUseCase(mediaSaverPort))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveToGallery_emitsSuccessMessageWhenSaveSucceeds() =
        runTest {
            coEvery { mediaSaverPort.saveToGallery(any(), any()) } returns Result.success("content://saved")

            viewModel.saveFeedback.test {
                viewModel.saveToGallery(sampleAsset())
                assertEquals(StudioDetailViewModel.SAVE_SUCCESS_MESSAGE, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveToGallery_emitsFailureMessageWhenSaveFails() =
        runTest {
            coEvery { mediaSaverPort.saveToGallery(any(), any()) } returns Result.failure(IllegalStateException("missing"))

            viewModel.saveFeedback.test {
                viewModel.saveToGallery(sampleAsset())
                assertEquals(StudioDetailViewModel.SAVE_FAILURE_MESSAGE, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveToGallery_passesEffectiveLocalUriForPersistedFile() =
        runTest {
            val file = File.createTempFile("studio", ".jpg")
            file.writeBytes(byteArrayOf(1, 2, 3))
            val persistedUri = "file://${file.absolutePath}"
            val uriSlot = slot<String>()
            val typeSlot = slot<MediaCapability>()

            coEvery {
                mediaSaverPort.saveToGallery(capture(uriSlot), capture(typeSlot))
            } returns Result.success("content://saved")

            viewModel.saveToGallery(
                StudioMediaUi(
                    id = "perm-1",
                    localUri = persistedUri,
                    prompt = "Prompt",
                    mediaType = MediaCapability.IMAGE,
                    createdAt = 1L,
                ),
            )

            coVerify { mediaSaverPort.saveToGallery(any(), any()) }
            assertEquals(persistedUri, uriSlot.captured)
            assertEquals(MediaCapability.IMAGE, typeSlot.captured)
            file.delete()
        }

    @Test
    fun saveToGallery_doesNotEmitFeedbackWhenSaveNotInvoked() =
        runTest {
            viewModel.saveFeedback.test {
                expectNoEvents()
            }
        }

    private fun sampleAsset(): StudioMediaUi =
        StudioMediaUi(
            id = "asset-1",
            localUri = "file:///data/user/0/com.app/files/studio.jpg",
            prompt = "Prompt",
            mediaType = MediaCapability.IMAGE,
            createdAt = 1L,
        )
}