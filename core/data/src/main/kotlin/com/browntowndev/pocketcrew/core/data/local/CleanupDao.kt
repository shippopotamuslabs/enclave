package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CleanupDao {
    @Transaction
    suspend fun clearUserData() {
        deleteChats()
        deleteMessages()
        deleteChatSummaries()
        deleteMessageFiles()
        deleteVisionAnalysis()
        deleteMemories()
        deleteStudioMedia()
        deleteStudioAlbums()
        deleteApiCredentials()
        deleteApiDefaultModels()
        deleteWebSearchSources()
    }

    @Query("DELETE FROM chat")
    suspend fun deleteChats()

    @Query("DELETE FROM message")
    suspend fun deleteMessages()

    @Query("DELETE FROM chat_summary")
    suspend fun deleteChatSummaries()

    @Query("DELETE FROM message_file")
    suspend fun deleteMessageFiles()

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

    @Query("DELETE FROM default_models WHERE api_config_id IS NOT NULL")
    suspend fun deleteApiDefaultModels()

    @Query("DELETE FROM web_search_source")
    suspend fun deleteWebSearchSources()
}
