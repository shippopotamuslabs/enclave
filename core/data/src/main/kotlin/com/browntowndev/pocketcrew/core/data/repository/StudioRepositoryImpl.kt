package com.browntowndev.pocketcrew.core.data.repository

import android.content.Context
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumEntity
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
import com.browntowndev.pocketcrew.domain.port.repository.StudioAlbumAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudioRepositoryImpl
    @Inject
    constructor(
        private val studioMediaDao: StudioMediaDao,
        private val studioAlbumDao: StudioAlbumDao,
        private val transactionProvider: TransactionProvider,
        private val syncTrigger: CloudSyncTrigger,
        private val localEncryption: LocalDataEncryptionService,
        @param:ApplicationContext private val context: Context,
    ) : StudioRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        private val ephemeralMedia = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
        private val generationError = MutableStateFlow<String?>(null)

        override fun observeAllMedia(): Flow<List<StudioMediaAsset>> =
            studioMediaDao.getAllMedia().map { entities ->
                entities.map { it.toDomain() }
            }

        override fun observeAllAlbums(): Flow<List<StudioAlbumAsset>> =
            studioAlbumDao.observeAllAlbums().map { entities ->
                entities.map { it.toDomain() }
            }

        override fun observeEphemeralMedia(): Flow<List<StudioMediaAsset>> = ephemeralMedia

        override fun observeGenerationError(): Flow<String?> = generationError

        override suspend fun reportGenerationError(message: String?) {
            generationError.value = message
        }

        override suspend fun clearGenerationError() {
            generationError.value = null
        }

        override suspend fun addEphemeralMedia(asset: StudioMediaAsset) {
            if (ephemeralMedia.value.any { it.id == asset.id }) return
            ephemeralMedia.value = ephemeralMedia.value + asset
        }

        override suspend fun updateEphemeralMedia(asset: StudioMediaAsset) {
            ephemeralMedia.value = ephemeralMedia.value.map { if (it.id == asset.id) asset else it }
        }

        override suspend fun removeEphemeralMedia(id: String) {
            ephemeralMedia.value = ephemeralMedia.value.filter { it.id != id }
        }

        override suspend fun saveMedia(
            localUri: String,
            prompt: String,
            mediaType: String,
            albumId: String?,
            createdAt: Long?,
        ): StudioMediaAsset {
            var finalUri = localUri
            // If the URI is in the cache directory, move it to files directory
            if (localUri.startsWith("file://")) {
                val file = File(localUri.replace("file://", ""))
                if (file.exists() && file.absolutePath.contains(context.cacheDir.path)) {
                    val destinationFile = File(context.filesDir, file.name)
                    file.copyTo(destinationFile, overwrite = true)
                    file.delete()
                    finalUri = "file://${destinationFile.absolutePath}"
                }
            }

            val newId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            studioMediaDao.insertMedia(
                StudioMediaEntity(
                    id = newId,
                    prompt = localEncryption.encryptString(prompt),
                    mediaUri = finalUri,
                    mediaType = mediaType,
                    createdAt = createdAt ?: System.currentTimeMillis(),
                    albumId = albumId.toPersistentAlbumId(),
                    pendingSync = 1,
                ),
            )
            triggerSync()
            return getMediaById(newId)!!
        }

        override suspend fun saveMedia(
            bytes: ByteArray,
            prompt: String,
            mediaType: String,
            albumId: String?,
            createdAt: Long?,
        ): StudioMediaAsset {
            val fileName = "studio_${System.currentTimeMillis()}.${if (mediaType == "IMAGE") "jpg" else "mp4"}"
            val file = File(context.filesDir, fileName)
            file.writeBytes(bytes)

            val persistedUri = "file://${file.absolutePath}"
            return saveMedia(persistedUri, prompt, mediaType, albumId, createdAt)
        }

        override suspend fun updateMedia(asset: StudioMediaAsset) {
            val existing = studioMediaDao.getMediaById(asset.id)
            studioMediaDao.insertMedia(
                StudioMediaEntity(
                    id = asset.id,
                    prompt = localEncryption.encryptString(asset.prompt),
                    mediaUri = asset.localUri,
                    mediaType = asset.mediaType,
                    createdAt = asset.createdAt,
                    albumId = asset.albumId.toPersistentAlbumId(),
                    remoteUrl = existing?.remoteUrl,
                    remoteThumbnailUrl = existing?.remoteThumbnailUrl,
                    syncStatus = existing?.syncStatus ?: "PENDING_UPLOAD",
                    pendingSync = 1,
                ),
            )
            triggerSync()
        }

        override suspend fun promoteEphemeralMedia(
            id: String,
            albumId: String?,
        ): StudioMediaAsset {
            val ephemeral =
                ephemeralMedia.value.find { it.id == id }
                    ?: throw IllegalArgumentException("Ephemeral media not found: $id")

            val savedAsset =
                saveMedia(
                    localUri = ephemeral.localUri,
                    prompt = ephemeral.prompt,
                    mediaType = ephemeral.mediaType,
                    albumId = albumId,
                    createdAt = ephemeral.createdAt,
                )

            // Remove from ephemeral list
            removeEphemeralMedia(id)

            return savedAsset
        }

        override suspend fun cacheEphemeralMedia(
            bytes: ByteArray,
            mediaType: String,
        ): String {
            val ephemeralDir = File(context.cacheDir, "studio_ephemeral")
            if (!ephemeralDir.exists()) {
                ephemeralDir.mkdirs()
            }

            val fileName = "ephemeral_${java.util.UUID.randomUUID()}.${if (mediaType == "IMAGE") "jpg" else "mp4"}"
            val file = File(ephemeralDir, fileName)
            file.writeBytes(bytes)
            val localUri = "file://${file.absolutePath}"
            return localUri
        }

        override suspend fun clearEphemeralCache() {
            val ephemeralDir = File(context.cacheDir, "studio_ephemeral")
            if (ephemeralDir.exists()) {
                ephemeralDir.deleteRecursively()
            }
            ephemeralMedia.value = emptyList()
            generationError.value = null
        }

        override suspend fun deleteMedia(id: String) {
            studioMediaDao.softDeleteById(id)
            triggerSync()
        }

        override suspend fun getMediaById(id: String): StudioMediaAsset? = studioMediaDao.getMediaById(id)?.toDomain()

        override suspend fun createAlbum(name: String): String {
            val id =
                java.util.UUID
                    .randomUUID()
                    .toString()
            studioAlbumDao.insertAlbum(StudioAlbumEntity(id = id, name = localEncryption.encryptString(name), pendingSync = 1))
            triggerSync()
            return id
        }

        override suspend fun renameAlbum(
            id: String,
            name: String,
        ) {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return
            studioAlbumDao.updateAlbumName(id, localEncryption.encryptString(trimmedName))
            triggerSync()
        }

        override suspend fun deleteAlbum(id: String) {
            transactionProvider.runInTransaction {
                studioMediaDao.reassignMediaToDefault(id)
                studioAlbumDao.softDeleteById(id)
            }
            triggerSync()
        }

        override suspend fun moveMediaToAlbum(
            mediaIds: List<String>,
            albumId: String,
        ) {
            studioMediaDao.updateMediaAlbum(
                mediaIds = mediaIds,
                albumId = albumId.toPersistentAlbumId(),
            )
            triggerSync()
        }

        override suspend fun readMediaBytes(localUri: String): ByteArray? {
            if (localUri.startsWith("content://")) {
                return context.contentResolver.openInputStream(android.net.Uri.parse(localUri))?.use { it.readBytes() }
            }

            val path = localUri.replace("file://", "")
            val file = File(path)
            return if (file.exists()) {
                file.readBytes()
            } else {
                null
            }
        }

        private fun StudioMediaEntity.toDomain() =
            StudioMediaAsset(
                id = id,
                localUri = localEncryption.decryptString(mediaUri),
                remoteUrl = remoteUrl?.let(localEncryption::decryptString),
                prompt = localEncryption.decryptString(prompt),
                mediaType = mediaType,
                createdAt = createdAt,
                albumId = albumId,
            )

        private fun StudioAlbumEntity.toDomain() =
            StudioAlbumAsset(
                id = id,
                name = localEncryption.decryptString(name),
            )

        private fun String?.toPersistentAlbumId(): String? = if (this == DEFAULT_GALLERY_ALBUM_ID) null else this
    }
