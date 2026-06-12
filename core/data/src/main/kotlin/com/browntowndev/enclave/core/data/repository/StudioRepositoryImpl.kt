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
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
import com.browntowndev.enclave.domain.port.repository.StudioAlbumAsset
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TooManyFunctions")
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
        private val activeGenerationPersistence: ActiveStudioGenerationPersistence,
        @param:ApplicationContext private val context: Context,
    ) : StudioRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val ephemeralMedia = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
        private val generationError = MutableStateFlow<String?>(null)
        private val generationProgress = MutableStateFlow<ImageGenerationProgress?>(null)
        private val activeGeneration = MutableStateFlow<ActiveStudioGeneration?>(null)
        private val decryptedMediaCache = mutableMapOf<String, Pair<Long, StudioMediaAsset>>()

        init {
            repositoryScope.launch {
                reconcilePersistedActiveGeneration()
            }
        }

        override fun observeAllMedia(): Flow<List<StudioMediaAsset>> =
            studioMediaDao.getAllMedia().mapLatest { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out =
                    entities.map { entity ->
                        val cacheKey = entity.id
                        val cached = decryptedMediaCache[cacheKey]
                        if (cached != null && cached.first == entity.updatedAt) {
                            cached.second
                        } else {
                            entity.toDomain(repairs).also { domain ->
                                decryptedMediaCache[cacheKey] = entity.updatedAt to domain
                            }
                        }
                    }
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

        override fun observeGenerationProgress(): Flow<ImageGenerationProgress?> = generationProgress

        override fun observeActiveGeneration(): Flow<ActiveStudioGeneration?> = activeGeneration

        override suspend fun reportGenerationError(message: String?) {
            generationError.value = message
        }

        override suspend fun clearGenerationError() {
            generationError.value = null
        }

        override fun reportGenerationProgress(progress: ImageGenerationProgress?) {
            generationProgress.update { progress }
        }

        override fun clearGenerationProgress() {
            generationProgress.update { null }
        }

        @Synchronized
        override fun reportActiveGeneration(generation: ActiveStudioGeneration): Boolean {
            if (activeGeneration.value != null) {
                return false
            }
            activeGeneration.update { generation }
            repositoryScope.launch {
                activeGenerationPersistence.persist(generation)
            }
            return true
        }

        @Synchronized
        override fun hasActiveGeneration(): Boolean = activeGeneration.value != null

        @Synchronized
        override fun clearActiveGeneration() {
            activeGeneration.update { null }
            repositoryScope.launch {
                activeGenerationPersistence.clear()
            }
        }

        @Synchronized
        override fun clearActiveGenerationIfOwned(generationId: String): Boolean {
            if (activeGeneration.value?.id != generationId) {
                return false
            }
            clearActiveGeneration()
            return true
        }

        @Synchronized
        override fun clearGenerationProgressIfOwned(generationId: String): Boolean {
            if (activeGeneration.value?.id != generationId) {
                return false
            }
            clearGenerationProgress()
            return true
        }

        override suspend fun reconcilePersistedActiveGeneration() {
            if (activeGenerationPersistence.load() == null || activeGeneration.value != null) {
                return
            }
            // Stale claims are expected after process death or an intentional session reset.
            // Clear them quietly; there is no in-process generation to resume.
            activeGenerationPersistence.clear()
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
        ): StudioMediaAsset =
            withContext(Dispatchers.IO) {
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
                getMediaById(newId)!!
            }

        override suspend fun saveMedia(
            bytes: ByteArray,
            prompt: String,
            mediaType: String,
            albumId: String?,
            createdAt: Long?,
        ): StudioMediaAsset =
            withContext(Dispatchers.IO) {
                val extension = StudioMediaExtensionResolver.extensionFor(mediaType)
                val fileName = "studio_${UUID.randomUUID()}.$extension"
                val file = File(context.filesDir, fileName)
                file.writeBytes(bytes)

                val persistedUri = "file://${file.absolutePath}"
                saveMedia(persistedUri, prompt, mediaType, albumId, createdAt)
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
        ): String =
            withContext(Dispatchers.IO) {
                val ephemeralDir = File(context.cacheDir, "studio_ephemeral")
                if (!ephemeralDir.exists()) {
                    ephemeralDir.mkdirs()
                }

                val extension = StudioMediaExtensionResolver.extensionFor(mediaType)
                val fileName = "ephemeral_${UUID.randomUUID()}.$extension"
                val file = File(ephemeralDir, fileName)
                file.writeBytes(bytes)
                "file://${file.absolutePath}"
            }

        override suspend fun clearEphemeralCache() {
            if (hasActiveGeneration()) {
                throw IllegalStateException("Cannot clear ephemeral cache while generation is active")
            }
            withContext(Dispatchers.IO) {
                val ephemeralDir = File(context.cacheDir, "studio_ephemeral")
                if (ephemeralDir.exists()) {
                    ephemeralDir.deleteRecursively()
                }
            }
            ephemeralMedia.value = emptyList()
            generationError.value = null
            generationProgress.update { null }
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

        override suspend fun getMediaPendingDownload(): List<StudioMediaAsset> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                studioMediaDao
                    .getUploaded()
                    .map { it.toDomain(repairs) }
                    .filter { asset -> !asset.remoteUrl.isNullOrBlank() && !localFileExists(asset.localUri) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun materializeDownloadedMedia(
            id: String,
            bytes: ByteArray,
        ) {
            withContext(Dispatchers.IO) {
                val raw = studioMediaDao.getMediaById(id) ?: return@withContext
                val plain = SensitiveEntityFieldEncryptors.decrypt(raw, healer, null)
                val extension = StudioMediaExtensionResolver.extensionFor(plain.mediaType)
                val file = File(context.filesDir, "studio_synced_$id.$extension")
                file.writeBytes(bytes)

                val updated = plain.copy(mediaUri = "file://${file.absolutePath}")
                studioMediaDao.insertMedia(SensitiveEntityFieldEncryptors.encrypt(updated, healer))
            }
        }

        private fun localFileExists(localUri: String): Boolean {
            if (localUri.startsWith("content://")) {
                return runCatching {
                    context.contentResolver.openInputStream(android.net.Uri.parse(localUri))?.use { true } ?: false
                }.getOrDefault(false)
            }
            val path = localUri.replace("file://", "")
            if (path.isBlank() || path.startsWith("http")) return false
            return File(path).exists()
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

        override suspend fun readMediaBytes(localUri: String): ByteArray? =
            withContext(Dispatchers.IO) {
                if (localUri.startsWith("content://")) {
                    return@withContext context.contentResolver.openInputStream(android.net.Uri.parse(localUri))?.use { it.readBytes() }
                }

                val path = localUri.replace("file://", "")
                val file = File(path)
                if (file.exists()) {
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
