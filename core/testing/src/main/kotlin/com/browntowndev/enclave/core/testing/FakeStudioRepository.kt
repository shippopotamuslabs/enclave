package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.domain.port.repository.StudioAlbumAsset
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [StudioRepositoryPort] fake (ENC-28). All observation streams are
 * StateFlow-backed so tests can drive them through the emit helpers; generation
 * ownership semantics mirror the real repository (a second report while one is
 * active is rejected). Write calls are recorded for state-based assertions.
 */
@Suppress("TooManyFunctions")
class FakeStudioRepository : StudioRepositoryPort {
    val mediaState = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
    val ephemeralState = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
    val albumsState = MutableStateFlow<List<StudioAlbumAsset>>(emptyList())
    val generationErrorState = MutableStateFlow<String?>(null)
    val generationProgressState = MutableStateFlow<ImageGenerationProgress?>(null)
    val activeGenerationState = MutableStateFlow<ActiveStudioGeneration?>(null)

    val getMediaByIdCalls = mutableListOf<String>()
    val reportGenerationErrorCalls = mutableListOf<String?>()
    val reportGenerationProgressCalls = mutableListOf<ImageGenerationProgress?>()
    val clearActiveGenerationIfOwnedCalls = mutableListOf<String>()
    val clearGenerationProgressIfOwnedCalls = mutableListOf<String>()
    var clearActiveGenerationCount = 0
    val addEphemeralMediaCalls = mutableListOf<StudioMediaAsset>()
    val promoteEphemeralMediaCalls = mutableListOf<Pair<String, String?>>()
    val saveMediaCalls = mutableListOf<String>()
    val updateMediaCalls = mutableListOf<StudioMediaAsset>()
    val deleteMediaCalls = mutableListOf<String>()
    val cacheEphemeralMediaCalls = mutableListOf<String>()

    /** When set, saveMedia returns this asset instead of creating one. */
    var saveMediaOverride: StudioMediaAsset? = null

    /** When set, promoteEphemeralMedia returns this asset instead of moving the stored one. */
    var promoteOverride: StudioMediaAsset? = null

    /** Uri returned by cacheEphemeralMedia. */
    var cacheEphemeralUri: String = "cache://ephemeral"

    var pendingDownload: List<StudioMediaAsset> = emptyList()
    val mediaBytes = mutableMapOf<String, ByteArray>()

    private var nextId = 1
    private var nextAlbumId = 1

    fun emitMedia(items: List<StudioMediaAsset>) {
        mediaState.value = items
    }

    fun emitEphemeral(items: List<StudioMediaAsset>) {
        ephemeralState.value = items
    }

    fun emitAlbums(items: List<StudioAlbumAsset>) {
        albumsState.value = items
    }

    override fun observeAllMedia(): Flow<List<StudioMediaAsset>> = mediaState

    override fun observeAllAlbums(): Flow<List<StudioAlbumAsset>> = albumsState

    override fun observeEphemeralMedia(): Flow<List<StudioMediaAsset>> = ephemeralState

    override fun observeGenerationError(): Flow<String?> = generationErrorState

    override fun observeGenerationProgress(): Flow<ImageGenerationProgress?> = generationProgressState

    override fun observeActiveGeneration(): Flow<ActiveStudioGeneration?> = activeGenerationState

    override suspend fun reportGenerationError(message: String?) {
        reportGenerationErrorCalls += message
        generationErrorState.value = message
    }

    override suspend fun clearGenerationError() {
        generationErrorState.value = null
    }

    override fun reportGenerationProgress(progress: ImageGenerationProgress?) {
        reportGenerationProgressCalls += progress
        generationProgressState.value = progress
    }

    override fun clearGenerationProgress() {
        generationProgressState.value = null
    }

    override fun reportActiveGeneration(generation: ActiveStudioGeneration): Boolean {
        if (activeGenerationState.value != null) return false
        activeGenerationState.value = generation
        return true
    }

    override fun hasActiveGeneration(): Boolean = activeGenerationState.value != null

    override fun clearActiveGeneration() {
        clearActiveGenerationCount++
        activeGenerationState.value = null
    }

    override fun clearActiveGenerationIfOwned(generationId: String): Boolean {
        clearActiveGenerationIfOwnedCalls += generationId
        if (activeGenerationState.value?.id != generationId) return false
        activeGenerationState.value = null
        return true
    }

    override fun clearGenerationProgressIfOwned(generationId: String): Boolean {
        clearGenerationProgressIfOwnedCalls += generationId
        if (activeGenerationState.value?.id != generationId) return false
        generationProgressState.value = null
        return true
    }

    override suspend fun reconcilePersistedActiveGeneration() = Unit

    override suspend fun addEphemeralMedia(asset: StudioMediaAsset) {
        addEphemeralMediaCalls += asset
        ephemeralState.value = ephemeralState.value + asset
    }

    override suspend fun updateEphemeralMedia(asset: StudioMediaAsset) {
        ephemeralState.value = ephemeralState.value.map { if (it.id == asset.id) asset else it }
    }

    override suspend fun removeEphemeralMedia(id: String) {
        ephemeralState.value = ephemeralState.value.filter { it.id != id }
    }

    override suspend fun saveMedia(
        localUri: String,
        prompt: String,
        mediaType: String,
        albumId: String?,
        createdAt: Long?,
    ): StudioMediaAsset {
        saveMediaCalls += localUri
        saveMediaOverride?.let { return it }
        val asset =
            StudioMediaAsset(
                id = "perm-${nextId++}",
                localUri = localUri,
                prompt = prompt,
                mediaType = mediaType,
                createdAt = createdAt ?: 1L,
                albumId = albumId,
            )
        mediaState.value = mediaState.value + asset
        return asset
    }

    override suspend fun saveMedia(
        bytes: ByteArray,
        prompt: String,
        mediaType: String,
        albumId: String?,
        createdAt: Long?,
    ): StudioMediaAsset {
        saveMediaCalls += "bytes:${bytes.size}"
        saveMediaOverride?.let { return it }
        val asset =
            StudioMediaAsset(
                id = "perm-${nextId++}",
                localUri = "bytes",
                prompt = prompt,
                mediaType = mediaType,
                createdAt = createdAt ?: 1L,
                albumId = albumId,
            )
        mediaState.value = mediaState.value + asset
        return asset
    }

    override suspend fun promoteEphemeralMedia(id: String, albumId: String?): StudioMediaAsset {
        promoteEphemeralMediaCalls += id to albumId
        promoteOverride?.let { return it }
        val existing = ephemeralState.value.firstOrNull { it.id == id }
        val asset =
            existing?.copy(albumId = albumId)
                ?: StudioMediaAsset(
                    id = "perm-$id",
                    localUri = "files://$id",
                    prompt = "p",
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = albumId,
                )
        ephemeralState.value = ephemeralState.value.filter { it.id != id }
        mediaState.value = mediaState.value + asset
        return asset
    }

    override suspend fun updateMedia(asset: StudioMediaAsset) {
        updateMediaCalls += asset
        mediaState.value = mediaState.value.map { if (it.id == asset.id) asset else it }
    }

    override suspend fun cacheEphemeralMedia(bytes: ByteArray, mediaType: String): String {
        cacheEphemeralMediaCalls += mediaType
        return cacheEphemeralUri
    }

    override suspend fun clearEphemeralCache() {
        ephemeralState.value = emptyList()
    }

    override suspend fun deleteMedia(id: String) {
        deleteMediaCalls += id
        mediaState.value = mediaState.value.filter { it.id != id }
        ephemeralState.value = ephemeralState.value.filter { it.id != id }
    }

    override suspend fun deleteAllMedia() {
        mediaState.value = emptyList()
        ephemeralState.value = emptyList()
    }

    override suspend fun getMediaById(id: String): StudioMediaAsset? {
        getMediaByIdCalls += id
        return mediaState.value.firstOrNull { it.id == id }
            ?: ephemeralState.value.firstOrNull { it.id == id }
    }

    override suspend fun getMediaPendingDownload(): List<StudioMediaAsset> = pendingDownload

    override suspend fun materializeDownloadedMedia(id: String, bytes: ByteArray) {
        mediaBytes[id] = bytes
    }

    override suspend fun createAlbum(name: String): String {
        val id = "album-${nextAlbumId++}"
        albumsState.value = albumsState.value + StudioAlbumAsset(id = id, name = name)
        return id
    }

    override suspend fun renameAlbum(id: String, name: String) {
        albumsState.value = albumsState.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteAlbum(id: String) {
        albumsState.value = albumsState.value.filter { it.id != id }
    }

    override suspend fun moveMediaToAlbum(mediaIds: List<String>, albumId: String) {
        mediaState.value =
            mediaState.value.map { if (it.id in mediaIds) it.copy(albumId = albumId) else it }
    }

    override suspend fun readMediaBytes(localUri: String): ByteArray? = mediaBytes[localUri]
}
