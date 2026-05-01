package com.browntowndev.pocketcrew.core.data.repository

import android.content.Context
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumEntity
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.domain.port.repository.StudioAlbumAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudioRepositoryImpl @Inject constructor(
    private val studioMediaDao: StudioMediaDao,
    private val studioAlbumDao: StudioAlbumDao,
    private val transactionProvider: TransactionProvider,
    @ApplicationContext private val context: Context
) : StudioRepositoryPort {

    override fun observeAllMedia(): Flow<List<StudioMediaAsset>> {
        return studioMediaDao.getAllMedia().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllAlbums(): Flow<List<StudioAlbumAsset>> {
        return studioAlbumDao.observeAllAlbums().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveMedia(localUri: String, prompt: String, mediaType: String, albumId: String?): StudioMediaAsset {
        var finalUri = localUri
        // If the URI is in the cache directory, move it to files directory
        if (localUri.contains(context.cacheDir.path)) {
            val file = File(localUri.replace("file://", ""))
            val fileName = file.name
            val sourceFile = File(context.cacheDir, "studio_ephemeral/$fileName")
            if (sourceFile.exists()) {
                val destinationFile = File(context.filesDir, fileName)
                sourceFile.copyTo(destinationFile, overwrite = true)
                finalUri = "file://${destinationFile.absolutePath}"
            }
        }

        val newId = studioMediaDao.insertMedia(
            StudioMediaEntity(
                prompt = prompt,
                mediaUri = finalUri,
                mediaType = mediaType,
                createdAt = System.currentTimeMillis(),
                albumId = albumId?.toLongOrNull()
            )
        )
        return getMediaById(newId.toString())!!
    }

    override suspend fun saveMedia(bytes: ByteArray, prompt: String, mediaType: String, albumId: String?): StudioMediaAsset {
        val fileName = "studio_${System.currentTimeMillis()}.${if (mediaType == "IMAGE") "jpg" else "mp4"}"
        val file = File(context.filesDir, fileName)
        file.writeBytes(bytes)
        
        val persistedUri = "file://${file.absolutePath}"
        return saveMedia(persistedUri, prompt, mediaType, albumId)
    }

    override suspend fun cacheEphemeralMedia(bytes: ByteArray, mediaType: String): String {
        val ephemeralDir = File(context.cacheDir, "studio_ephemeral")
        if (!ephemeralDir.exists()) {
            ephemeralDir.mkdirs()
        }
        
        val fileName = "ephemeral_${System.currentTimeMillis()}.${if (mediaType == "IMAGE") "jpg" else "mp4"}"
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
    }

    override suspend fun deleteMedia(id: String) {
        val longId = id.toLongOrNull()
        if (longId != null) {
            studioMediaDao.deleteMedia(longId)
        }
    }

    override suspend fun getMediaById(id: String): StudioMediaAsset? {
        val longId = id.toLongOrNull() ?: return null
        return studioMediaDao.getMediaById(longId)?.toDomain()
    }

    override suspend fun createAlbum(name: String): String {
        return studioAlbumDao.insertAlbum(StudioAlbumEntity(name = name)).toString()
    }

    override suspend fun renameAlbum(id: String, name: String) {
        val longId = id.toLongOrNull() ?: return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return
        }

        studioAlbumDao.updateAlbumName(longId, trimmedName)
    }

    override suspend fun deleteAlbum(id: String) {
        val longId = id.toLongOrNull() ?: return

        transactionProvider.runInTransaction {
            studioMediaDao.reassignMediaToDefault(longId)
            studioAlbumDao.deleteAlbum(longId)
        }
    }

    override suspend fun moveMediaToAlbum(mediaIds: List<String>, albumId: String) {
        studioMediaDao.updateMediaAlbum(
            mediaIds = mediaIds.map { it.toLong() },
            albumId = albumId.toLong()
        )
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

    private fun StudioMediaEntity.toDomain() = StudioMediaAsset(
        id = id.toString(),
        localUri = mediaUri,
        prompt = prompt,
        mediaType = mediaType,
        createdAt = createdAt,
        albumId = albumId?.toString(),
    )

    private fun StudioAlbumEntity.toDomain() = StudioAlbumAsset(
        id = id.toString(),
        name = name,
    )
}
