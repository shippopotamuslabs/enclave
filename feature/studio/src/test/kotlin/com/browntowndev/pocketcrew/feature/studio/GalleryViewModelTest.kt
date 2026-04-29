package com.browntowndev.pocketcrew.feature.studio

import app.cash.turbine.test
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {
    private val repository = FakeStudioRepository()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ui state exposes existing media in default album`() = runTest {
        repository.emitMedia(
            listOf(
                studioMediaAsset(id = "asset-1"),
                studioMediaAsset(id = "asset-2"),
            ),
        )
        val viewModel = GalleryViewModel(repository)

        viewModel.uiState.test {
            val state = awaitItem()
            val defaultAlbum = state.albums.single()

            assertEquals(DEFAULT_GALLERY_ALBUM_ID, defaultAlbum.id)
            assertEquals("Default Album", defaultAlbum.name)
            assertEquals(2, defaultAlbum.itemCount)
            assertEquals(listOf("asset-1", "asset-2"), defaultAlbum.items.map { it.id })
        }
    }

    @Test
    fun `add album trims name and appends empty album`() = runTest {
        val viewModel = GalleryViewModel(repository)

        viewModel.uiState.test {
            assertEquals(listOf("Default Album"), awaitItem().albums.map { it.name })

            viewModel.addAlbum("  Moodboards  ")

            val state = awaitItem()
            assertEquals(listOf("Default Album", "Moodboards"), state.albums.map { it.name })
            assertEquals(0, state.albums.last().itemCount)
        }
    }

    @Test
    fun `add album ignores blank names`() = runTest {
        val viewModel = GalleryViewModel(repository)

        viewModel.uiState.test {
            val initialState = awaitItem()

            viewModel.addAlbum("   ")

            expectNoEvents()
            assertEquals(listOf("Default Album"), initialState.albums.map { it.name })
        }
    }

    private fun studioMediaAsset(id: String): StudioMediaAsset =
        StudioMediaAsset(
            id = id,
            localUri = "content://$id",
            prompt = "prompt",
            mediaType = MediaCapability.IMAGE.name,
            createdAt = 1L,
        )
}

private class FakeStudioRepository : StudioRepositoryPort {
    private val media = MutableStateFlow<List<StudioMediaAsset>>(emptyList())

    override fun observeAllMedia(): Flow<List<StudioMediaAsset>> = media

    fun emitMedia(items: List<StudioMediaAsset>) {
        media.value = items
    }

    override suspend fun saveMedia(localUri: String, prompt: String, mediaType: String) = Unit

    override suspend fun saveMedia(bytes: ByteArray, prompt: String, mediaType: String) = Unit

    override suspend fun deleteMedia(id: String) = Unit

    override suspend fun getMediaById(id: String): StudioMediaAsset? = media.value.firstOrNull { it.id == id }
}
