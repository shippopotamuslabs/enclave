package com.browntowndev.pocketcrew.domain.port.repository

import kotlinx.coroutines.flow.Flow

interface StudioMediaId {
    val value: String
}

data class StudioMediaAsset(
    val id: String,
    val localUri: String,
    val prompt: String,
    val mediaType: String, // IMAGE/VIDEO
    val createdAt: Long
)

interface StudioRepositoryPort {
    fun observeAllMedia(): Flow<List<StudioMediaAsset>>
    suspend fun saveMedia(localUri: String, prompt: String, mediaType: String)
    suspend fun saveMedia(bytes: ByteArray, prompt: String, mediaType: String)
    suspend fun deleteMedia(id: String)
    suspend fun getMediaById(id: String): StudioMediaAsset?
}
