package com.browntowndev.pocketcrew.core.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudioRepositoryImpl @Inject constructor(
    private val studioMediaDao: StudioMediaDao,
    @ApplicationContext private val context: Context
) : StudioRepositoryPort {

    override fun observeAllMedia(): Flow<List<StudioMediaAsset>> {
        return studioMediaDao.getAllMedia().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveMedia(localUri: String, prompt: String, mediaType: String) {
        studioMediaDao.insertMedia(
            StudioMediaEntity(
                prompt = prompt,
                mediaUri = localUri,
                mediaType = mediaType,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun saveMedia(bytes: ByteArray, prompt: String, mediaType: String) {
        val fileName = "studio_${System.currentTimeMillis()}.${if (mediaType == "IMAGE") "jpg" else "mp4"}"
        val file = File(context.filesDir, fileName)
        file.writeBytes(bytes)
        
        saveMedia(file.toUri().toString(), prompt, mediaType)
    }

    override suspend fun deleteMedia(id: String) {
        studioMediaDao.deleteMedia(id.toLong())
    }

    override suspend fun getMediaById(id: String): StudioMediaAsset? {
        return studioMediaDao.getMediaById(id.toLong())?.toDomain()
    }

    private fun StudioMediaEntity.toDomain() = StudioMediaAsset(
        id = id.toString(),
        localUri = mediaUri,
        prompt = prompt,
        mediaType = mediaType,
        createdAt = createdAt
    )
}
