package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
@Suppress("TooManyFunctions")
interface CleanupDao {
    @Transaction
    suspend fun clearUserData() {
        clearUserContent()
        // default_models RESTRICT-deletes to local/api configs and tts/media providers — wipe first.
        deleteAllDefaultModels()
        deleteApiCredentials()
        deleteApiModelConfigurations()
        deleteLocalModelConfigurations()
        deleteLocalModels()
        deleteUtilityModels()
        deleteTtsProviders()
        deleteMediaProviders()
    }

    /**
     * Wipes user-generated content (chats, messages, memories, studio, embeddings, web search)
     * but preserves model/provider configuration so a fresh logout leaves the app usable for
     * guest mode. Use [clearUserData] for account deletion.
     */
    @Transaction
    suspend fun clearUserContent() {
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
        deleteEmbeddings()
        runCatching {
            deleteMemoryEmbeddings(SimpleSQLiteQuery("DELETE FROM memory_embeddings"))
        }
        runCatching {
            deleteDocumentEmbeddings(SimpleSQLiteQuery("DELETE FROM document_embeddings"))
        }
        runCatching {
            deleteArtifactBlockEmbeddings(SimpleSQLiteQuery("DELETE FROM artifact_block_embeddings"))
        }
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

    @Query("DELETE FROM default_models")
    suspend fun deleteAllDefaultModels()

    @Query("DELETE FROM local_model_configurations")
    suspend fun deleteLocalModelConfigurations()

    @Query("DELETE FROM local_models")
    suspend fun deleteLocalModels()

    @Query("DELETE FROM utility_models")
    suspend fun deleteUtilityModels()

    @Query("DELETE FROM tts_providers")
    suspend fun deleteTtsProviders()

    @Query("DELETE FROM media_providers")
    suspend fun deleteMediaProviders()

    @Query("DELETE FROM embeddings")
    suspend fun deleteEmbeddings()

    @RawQuery
    suspend fun deleteMemoryEmbeddings(query: SupportSQLiteQuery): Long

    @RawQuery
    suspend fun deleteDocumentEmbeddings(query: SupportSQLiteQuery): Long

    @RawQuery
    suspend fun deleteArtifactBlockEmbeddings(query: SupportSQLiteQuery): Long

    @Query("DELETE FROM web_search_source")
    suspend fun deleteWebSearchSources()
}
