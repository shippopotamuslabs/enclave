package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import kotlinx.coroutines.flow.Flow

interface StudioMediaId {
    val value: String
}

const val DEFAULT_GALLERY_ALBUM_ID = "default"

data class StudioMediaAsset(
    val id: String,
    val localUri: String,
    val remoteUrl: String? = null,
    val prompt: String,
    val mediaType: String, // IMAGE/VIDEO
    val createdAt: Long,
    val albumId: String? = null,
)

data class StudioAlbumAsset(
    val id: String,
    val name: String,
)

data class ActiveStudioGeneration(
    val id: String,
    val prompt: String,
    val mediaType: MediaCapability,
    val settings: GenerationSettings,
)

@Suppress("TooManyFunctions")
interface StudioRepositoryPort {
    fun observeAllMedia(): Flow<List<StudioMediaAsset>>

    fun observeAllAlbums(): Flow<List<StudioAlbumAsset>>

    fun observeEphemeralMedia(): Flow<List<StudioMediaAsset>>

    fun observeGenerationError(): Flow<String?>

    fun observeGenerationProgress(): Flow<ImageGenerationProgress?>

    fun observeActiveGeneration(): Flow<ActiveStudioGeneration?>

    suspend fun reportGenerationError(message: String?)

    suspend fun clearGenerationError()

    fun reportGenerationProgress(progress: ImageGenerationProgress?)

    fun clearGenerationProgress()

    fun reportActiveGeneration(generation: ActiveStudioGeneration): Boolean

    fun hasActiveGeneration(): Boolean

    fun clearActiveGeneration()

    fun clearActiveGenerationIfOwned(generationId: String): Boolean

    fun clearGenerationProgressIfOwned(generationId: String): Boolean

    suspend fun reconcilePersistedActiveGeneration()

    suspend fun addEphemeralMedia(asset: StudioMediaAsset)

    suspend fun updateEphemeralMedia(asset: StudioMediaAsset)

    suspend fun removeEphemeralMedia(id: String)

    suspend fun saveMedia(
        localUri: String,
        prompt: String,
        mediaType: String,
        albumId: String? = null,
        createdAt: Long? = null,
    ): StudioMediaAsset

    suspend fun saveMedia(
        bytes: ByteArray,
        prompt: String,
        mediaType: String,
        albumId: String? = null,
        createdAt: Long? = null,
    ): StudioMediaAsset

    suspend fun promoteEphemeralMedia(
        id: String,
        albumId: String?,
    ): StudioMediaAsset

    suspend fun updateMedia(asset: StudioMediaAsset)

    suspend fun cacheEphemeralMedia(
        bytes: ByteArray,
        mediaType: String,
    ): String

    suspend fun clearEphemeralCache()

    suspend fun deleteMedia(id: String)

    suspend fun deleteAllMedia()

    suspend fun getMediaById(id: String): StudioMediaAsset?

    /**
     * Cloud-uploaded media whose bytes are not yet present on this device (synced-down rows whose
     * [StudioMediaAsset.localUri] still points at the remote URL). The cloud download worker fetches
     * + decrypts these and calls [materializeDownloadedMedia].
     */
    suspend fun getMediaPendingDownload(): List<StudioMediaAsset>

    /**
     * Persists decrypted [bytes] for an already-synced media row to a local file and repoints its
     * [StudioMediaAsset.localUri] at it, without marking the row for re-upload.
     */
    suspend fun materializeDownloadedMedia(
        id: String,
        bytes: ByteArray,
    )

    suspend fun createAlbum(name: String): String

    suspend fun renameAlbum(
        id: String,
        name: String,
    )

    suspend fun deleteAlbum(id: String)

    suspend fun moveMediaToAlbum(
        mediaIds: List<String>,
        albumId: String,
    )

    suspend fun readMediaBytes(localUri: String): ByteArray?
}
