package com.browntowndev.enclave.core.data.repository

import android.content.Context
import com.browntowndev.enclave.core.data.local.CleanupDao
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioAlbumEntity
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
import com.browntowndev.enclave.domain.port.repository.StudioAlbumAsset
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
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
        private val cleanupDao: CleanupDao,
        private val syncTrigger: CloudSyncTrigger,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
        @param:ApplicationContext private val context: Context,
    ) : StudioRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        private val ephemeralMedia = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
        private val generationError = MutableStateFlow<String?>(null)

        override fun observeAllMedia(): Flow<List<StudioMediaAsset>> =
            studioMediaDao.getAllMedia().mapLatest { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { it.toDomain(repairs) }
                readRepairApplier.apply(repairs)
                out
            }

        override fun observeAllAlbums(): Flow<List<StudioAlbumAsset>> =
            studioAlbumDao.observeAllAlbums().mapLatest { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { it.toDomain(repairs) }
                readRepairApplier.apply(repairs)
                out
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
            val plain =
                StudioMediaEntity(
                    id = newId,
                    prompt = prompt,
                    mediaUri = finalUri,
                    mediaType = mediaType,
                    createdAt = createdAt ?: System.currentTimeMillis(),
                    albumId = albumId.toPersistentAlbumId(),
                    pendingSync = 1,
                )
            studioMediaDao.insertMedia(SensitiveEntityFieldEncryptors.encrypt(plain, healer))
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
            val plain =
                StudioMediaEntity(
                    id = asset.id,
                    prompt = asset.prompt,
                    mediaUri = asset.localUri,
                    mediaType = asset.mediaType,
                    createdAt = asset.createdAt,
                    albumId = asset.albumId.toPersistentAlbumId(),
                    remoteUrl = existing?.remoteUrl,
                    remoteThumbnailUrl = existing?.remoteThumbnailUrl,
                    syncStatus = existing?.syncStatus ?: "PENDING_UPLOAD",
                    stagingRemoteUrl = existing?.stagingRemoteUrl,
                    pendingSync = 1,
                )
            studioMediaDao.insertMedia(SensitiveEntityFieldEncryptors.encrypt(plain, healer))
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
            return "file://${file.absolutePath}"
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

        override suspend fun deleteAllMedia() {
            transactionProvider.runInTransaction {
                cleanupDao.deleteStudioMedia()
                cleanupDao.deleteStudioAlbums()
            }
            syncTrigger.triggerCloudMediaPurge()
        }

        override suspend fun getMediaById(id: String): StudioMediaAsset? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = studioMediaDao.getMediaById(id)?.toDomain(repairs)
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun createAlbum(name: String): String {
            val id =
                java.util.UUID
                    .randomUUID()
                    .toString()
            val plain = StudioAlbumEntity(id = id, name = name.trim(), pendingSync = 1)
            studioAlbumDao.insertAlbum(SensitiveEntityFieldEncryptors.encrypt(plain, healer))
            triggerSync()
            return id
        }

        override suspend fun renameAlbum(
            id: String,
            name: String,
        ) {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return
            val existing = studioAlbumDao.getAlbumById(id) ?: return
            studioAlbumDao.insertAlbum(
                SensitiveEntityFieldEncryptors.encrypt(existing.copy(name = trimmedName, pendingSync = 1), healer),
            )
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

        private fun StudioMediaEntity.toDomain(readRepairs: MutableList<SensitiveRoomReadRepair>?): StudioMediaAsset {
            val e = SensitiveEntityFieldEncryptors.decrypt(this, healer, readRepairs)
            return StudioMediaAsset(
                id = e.id,
                localUri = e.mediaUri,
                remoteUrl = e.remoteUrl,
                prompt = e.prompt,
                mediaType = e.mediaType,
                createdAt = e.createdAt,
                albumId = e.albumId,
            )
        }

        private fun StudioAlbumEntity.toDomain(readRepairs: MutableList<SensitiveRoomReadRepair>?): StudioAlbumAsset {
            val e = SensitiveEntityFieldEncryptors.decrypt(this, healer, readRepairs)
            return StudioAlbumAsset(
                id = e.id,
                name = e.name,
            )
        }

        private fun String?.toPersistentAlbumId(): String? = if (this == DEFAULT_GALLERY_ALBUM_ID) null else this
    }
