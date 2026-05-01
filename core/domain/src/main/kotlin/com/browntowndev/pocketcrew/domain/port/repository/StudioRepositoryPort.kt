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
    val createdAt: Long,
    val albumId: String? = null,
)

data class StudioAlbumAsset(
    val id: String,
    val name: String,
)

interface StudioRepositoryPort {
    fun observeAllMedia(): Flow<List<StudioMediaAsset>>
    fun observeAllAlbums(): Flow<List<StudioAlbumAsset>>
    suspend fun saveMedia(localUri: String, prompt: String, mediaType: String, albumId: String? = null): StudioMediaAsset
    suspend fun saveMedia(bytes: ByteArray, prompt: String, mediaType: String, albumId: String? = null): StudioMediaAsset
    suspend fun cacheEphemeralMedia(bytes: ByteArray, mediaType: String): String
    suspend fun clearEphemeralCache()
    suspend fun deleteMedia(id: String)
    suspend fun getMediaById(id: String): StudioMediaAsset?
    suspend fun createAlbum(name: String): String
    suspend fun renameAlbum(id: String, name: String)
    suspend fun deleteAlbum(id: String)
    suspend fun moveMediaToAlbum(mediaIds: List<String>, albumId: String)
    suspend fun readMediaBytes(localUri: String): ByteArray?
}
