package com.browntowndev.pocketcrew.feature.studio

import app.cash.turbine.test
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioAlbumAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import io.mockk.mockk
import io.mockk.verify
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
    private val shareMediaPort = mockk<ShareMediaPort>(relaxed = true)

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
        val viewModel = GalleryViewModel(repository, shareMediaPort)

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
        val viewModel = GalleryViewModel(repository, shareMediaPort)

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
        val viewModel = GalleryViewModel(repository, shareMediaPort)

        viewModel.uiState.test {
            val initialState = awaitItem()

            viewModel.addAlbum("   ")

            expectNoEvents()
            assertEquals(listOf("Default Album"), initialState.albums.map { it.name })
        }
    }

    @Test
    fun `rename album updates album name`() = runTest {
        val viewModel = GalleryViewModel(repository, shareMediaPort)
        viewModel.addAlbum("Old Name")
        
        viewModel.uiState.test {
            val stateWithAlbum = awaitItem()
            val albumId = stateWithAlbum.albums.last().id
            
            viewModel.renameAlbum(albumId, "New Name")
            
            val updatedState = awaitItem()
            assertEquals("New Name", updatedState.albums.last().name)
        }
    }

    @Test
    fun `rename album ignores blank names`() = runTest {
        val viewModel = GalleryViewModel(repository, shareMediaPort)
        viewModel.addAlbum("Old Name")
        
        viewModel.uiState.test {
            val stateWithAlbum = awaitItem()
            val albumId = stateWithAlbum.albums.last().id
            
            viewModel.renameAlbum(albumId, "   ")
            
            expectNoEvents()
            assertEquals("Old Name", stateWithAlbum.albums.last().name)
        }
    }

    @Test
    fun `deleteMedia removes specified items`() = runTest {
        repository.emitMedia(
            listOf(
                studioMediaAsset(id = "asset-1"),
                studioMediaAsset(id = "asset-2"),
                studioMediaAsset(id = "asset-3"),
            )
        )
        val viewModel = GalleryViewModel(repository, shareMediaPort)

        viewModel.uiState.test {
            assertEquals(3, awaitItem().albums.single().itemCount)

            viewModel.deleteMedia(setOf("asset-1", "asset-3"))

            val nextState = awaitItem()
            assertEquals(1, nextState.albums.single().itemCount)
            assertEquals("asset-2", nextState.albums.single().items.single().id)
        }
    }

    @Test
    fun `deleteAlbums removes specified albums`() = runTest {
        val viewModel = GalleryViewModel(repository, shareMediaPort)
        viewModel.addAlbum("Album 1")
        viewModel.addAlbum("Album 2")
        viewModel.addAlbum("Album 3")
        
        viewModel.uiState.test {
            val initialState = awaitItem()
            val album1Id = initialState.albums.first { it.name == "Album 1" }.id
            val album3Id = initialState.albums.first { it.name == "Album 3" }.id
            
            viewModel.deleteAlbums(setOf(album1Id, album3Id))
            
            val nextState = awaitItem()
            val remainingAlbumNames = nextState.albums.map { it.name }
            assertEquals(listOf("Default Album", "Album 2"), remainingAlbumNames)
        }
    }

    @Test
    fun `shareMedia passes correct URIs to ShareMediaPort`() = runTest {
        repository.emitMedia(listOf(studioMediaAsset(id = "asset-1")))
        val viewModel = GalleryViewModel(repository, shareMediaPort)
        
        viewModel.uiState.test {
            awaitItem() // Initial
            viewModel.shareMedia(setOf("asset-1"))
            
            verify { shareMediaPort.shareMedia(listOf("content://asset-1"), "*/*") }
        }
    }

    @Test
    fun `shareSingleMedia passes correct URI and MIME type`() = runTest {
        repository.emitMedia(listOf(studioMediaAsset(id = "asset-1")))
        val viewModel = GalleryViewModel(repository, shareMediaPort)
        
        viewModel.uiState.test {
            awaitItem() // Initial
            viewModel.shareSingleMedia("asset-1")
            
            verify { shareMediaPort.shareMedia(listOf("content://asset-1"), "image/*") }
        }
    }

    @Test
    fun `moveMediaToAlbum moves items to specified album`() = runTest {
        repository.emitMedia(
            listOf(
                studioMediaAsset(id = "asset-1"),
                studioMediaAsset(id = "asset-2"),
            )
        )
        val viewModel = GalleryViewModel(repository, shareMediaPort)
        viewModel.addAlbum("Custom Album")

        viewModel.uiState.test {
            val initialState = awaitItem()
            val customAlbumId = initialState.albums.last().id

            viewModel.moveMediaToAlbum(setOf("asset-1"), customAlbumId)

            val updatedState = awaitItem()
            val defaultAlbum = updatedState.albums.first { it.id == DEFAULT_GALLERY_ALBUM_ID }
            val customAlbum = updatedState.albums.first { it.id == customAlbumId }

            assertEquals(1, defaultAlbum.itemCount)
            assertEquals("asset-2", defaultAlbum.items.single().id)

            assertEquals(1, customAlbum.itemCount)
            assertEquals("asset-1", customAlbum.items.single().id)
        }
    }

    @Test
    fun `onMediaItemMeasured updates aspect ratio in ui state`() = runTest {
        repository.emitMedia(
            listOf(
                studioMediaAsset(id = "asset-1"),
            )
        )
        val viewModel = GalleryViewModel(repository, shareMediaPort)

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals(null, initialState.albums.single().items.single().aspectRatio)

            viewModel.onMediaItemMeasured("asset-1", 1.5f)

            val updatedState = awaitItem()
            assertEquals(1.5f, updatedState.albums.single().items.single().aspectRatio)
            
            // Note: Turbine expects us to not use expectNoEvents without wait or advanceUntilIdle if coroutines are still running, but we can just test the state change.
        }
    }

    private fun studioMediaAsset(id: String): StudioMediaAsset =
        StudioMediaAsset(
            id = id,
            localUri = "content://$id",
            prompt = "prompt",
            mediaType = MediaCapability.IMAGE.name,
            createdAt = 1L,
            albumId = null,
        )
}

private class FakeStudioRepository : StudioRepositoryPort {
    private val media = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
    private val albums = MutableStateFlow<List<StudioAlbumAsset>>(emptyList())
    private var nextAlbumId = 1L

    override fun observeAllMedia(): Flow<List<StudioMediaAsset>> = media
    override fun observeAllAlbums(): Flow<List<StudioAlbumAsset>> = albums

    fun emitMedia(items: List<StudioMediaAsset>) {
        media.value = items
    }

    override suspend fun saveMedia(localUri: String, prompt: String, mediaType: String, albumId: String?): StudioMediaAsset =
        StudioMediaAsset(id = "1", localUri = localUri, prompt = prompt, mediaType = mediaType, createdAt = 1L, albumId = albumId)

    override suspend fun saveMedia(bytes: ByteArray, prompt: String, mediaType: String, albumId: String?): StudioMediaAsset =
        StudioMediaAsset(id = "1", localUri = "bytes", prompt = prompt, mediaType = mediaType, createdAt = 1L, albumId = albumId)

    override suspend fun cacheEphemeralMedia(bytes: ByteArray, mediaType: String): String = ""

    override suspend fun clearEphemeralCache() = Unit

    override suspend fun deleteMedia(id: String) {
        media.value = media.value.filter { it.id != id }
    }

    override suspend fun getMediaById(id: String): StudioMediaAsset? = media.value.firstOrNull { it.id == id }

    override suspend fun createAlbum(name: String): String {
        val id = nextAlbumId++.toString()
        albums.value = albums.value + StudioAlbumAsset(id = id, name = name)
        return id
    }

    override suspend fun renameAlbum(id: String, name: String) {
        albums.value = albums.value.map {
            if (it.id == id) it.copy(name = name) else it
        }
    }

    override suspend fun deleteAlbum(id: String) {
        albums.value = albums.value.filter { it.id != id }
    }

    override suspend fun moveMediaToAlbum(mediaIds: List<String>, albumId: String) {
        media.value = media.value.map {
            if (it.id in mediaIds) it.copy(albumId = albumId) else it
        }
    }

    override suspend fun readMediaBytes(localUri: String): ByteArray? = null
}
