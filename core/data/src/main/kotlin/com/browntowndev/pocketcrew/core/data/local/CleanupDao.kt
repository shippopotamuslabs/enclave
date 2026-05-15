package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
@Suppress("TooManyFunctions")
interface CleanupDao {
    @Transaction
    suspend fun clearUserData() {
        deleteChats()
        deleteMessages()
        deleteMessageTranscriptSegments()
        deleteChatSummaries()
        deleteMessageFiles()
        deleteDocuments()
        deleteVisionAnalysis()
        deleteMemories()
        deleteStudioMedia()
        deleteStudioAlbums()
        deleteApiCredentials()
        deleteApiModelConfigurations()
        deleteApiDefaultModels()
        deleteLocalModelConfigurations()
        deleteLocalModels()
        deleteTtsProviders()
        deleteMediaProviders()
        deleteEmbeddings()
        deleteWebSearchSources()
    }

    @Query("DELETE FROM chat")
    suspend fun deleteChats()

    @Query("DELETE FROM message")
    suspend fun deleteMessages()

    @Query("DELETE FROM message_transcript_segment")
    suspend fun deleteMessageTranscriptSegments()

    @Query("DELETE FROM chat_summary")
    suspend fun deleteChatSummaries()

    @Query("DELETE FROM message_file")
    suspend fun deleteMessageFiles()

    @Query("DELETE FROM documents")
    suspend fun deleteDocuments()

    @Query("DELETE FROM message_vision_analysis")
    suspend fun deleteVisionAnalysis()

    @Query("DELETE FROM memories")
    suspend fun deleteMemories()

    @Query("DELETE FROM studio_gallery")
    suspend fun deleteStudioMedia()

    @Query("DELETE FROM studio_albums")
    suspend fun deleteStudioAlbums()

    @Query("DELETE FROM api_credentials")
    suspend fun deleteApiCredentials()

    @Query("DELETE FROM api_model_configurations")
    suspend fun deleteApiModelConfigurations()

    @Query("DELETE FROM default_models WHERE api_config_id IS NOT NULL")
    suspend fun deleteApiDefaultModels()

    @Query("DELETE FROM local_model_configurations")
    suspend fun deleteLocalModelConfigurations()

    @Query("DELETE FROM local_models")
    suspend fun deleteLocalModels()

    @Query("DELETE FROM tts_providers")
    suspend fun deleteTtsProviders()

    @Query("DELETE FROM media_providers")
    suspend fun deleteMediaProviders()

    @Query("DELETE FROM embeddings")
    suspend fun deleteEmbeddings()

    @Query("DELETE FROM web_search_source")
    suspend fun deleteWebSearchSources()
}
