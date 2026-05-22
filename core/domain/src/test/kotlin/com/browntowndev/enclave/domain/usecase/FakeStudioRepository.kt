package com.browntowndev.enclave.domain.usecase

import com.browntowndev.enclave.domain.port.repository.StudioAlbumAsset
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight fake for settings cleanup tests — only media listing/deletion is functional. */
class FakeStudioRepository : StudioRepositoryPort {
    private val _allMedia = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
    private val deletedIds = mutableSetOf<String>()

    val deletedMediaIds: Set<String>
        get() = deletedIds.toSet()

    var deleteMediaFailure: RuntimeException? = null

    fun setMedia(assets: List<StudioMediaAsset>) {
        _allMedia.value = assets
    }

    override fun observeAllMedia(): Flow<List<StudioMediaAsset>> = _allMedia

    override suspend fun deleteMedia(id: String) {
        deleteMediaFailure?.let { throw it }
        deletedIds.add(id)
        _allMedia.value = _allMedia.value.filter { it.id != id }
    }

    override suspend fun deleteAllMedia() {
        deleteMediaFailure?.let { throw it }
        deletedIds.addAll(_allMedia.value.map { it.id })
        _allMedia.value = emptyList()
    }

    override fun observeAllAlbums(): Flow<List<StudioAlbumAsset>> = throw UnsupportedOperationException()

    override fun observeEphemeralMedia(): Flow<List<StudioMediaAsset>> = throw UnsupportedOperationException()

    override fun observeGenerationError(): Flow<String?> = throw UnsupportedOperationException()

    override suspend fun reportGenerationError(message: String?) = unsupported()

    override suspend fun clearGenerationError() = unsupported()

    override suspend fun addEphemeralMedia(asset: StudioMediaAsset) = unsupported()

    override suspend fun updateEphemeralMedia(asset: StudioMediaAsset) = unsupported()

    override suspend fun removeEphemeralMedia(id: String) = unsupported()

    override suspend fun saveMedia(
        localUri: String,
        prompt: String,
        mediaType: String,
        albumId: String?,
        createdAt: Long?,
    ): StudioMediaAsset = unsupported()

    override suspend fun saveMedia(
        bytes: ByteArray,
        prompt: String,
        mediaType: String,
        albumId: String?,
        createdAt: Long?,
    ): StudioMediaAsset = unsupported()

    override suspend fun promoteEphemeralMedia(
        id: String,
        albumId: String?,
    ): StudioMediaAsset = unsupported()

    override suspend fun updateMedia(asset: StudioMediaAsset) = unsupported()

    override suspend fun cacheEphemeralMedia(
        bytes: ByteArray,
        mediaType: String,
    ): String = unsupported()

    override suspend fun clearEphemeralCache() = unsupported()

    override suspend fun getMediaById(id: String): StudioMediaAsset? = unsupported()

    override suspend fun createAlbum(name: String): String = unsupported()

    override suspend fun renameAlbum(
        id: String,
        name: String,
    ) = unsupported()

    override suspend fun deleteAlbum(id: String) = unsupported()

    override suspend fun moveMediaToAlbum(
        mediaIds: List<String>,
        albumId: String,
    ) = unsupported()

    override suspend fun readMediaBytes(localUri: String): ByteArray? = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("FakeStudioRepository: not implemented")
}
