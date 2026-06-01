package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProfileCorpusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(entity: ProfileCorpusConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(entities: List<ProfileCorpusConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(entities: List<ProfileCorpusMessageEntity>)

    @Query("SELECT * FROM profile_corpus_conversations WHERE corpus_run_id = :runId ORDER BY created_at_ms DESC")
    suspend fun getConversationsForRun(runId: String): List<ProfileCorpusConversationEntity>

    @Query(
        "SELECT * FROM profile_corpus_conversations WHERE corpus_run_id = :runId " +
            "AND selected_for_analysis = 1 ORDER BY signal_score DESC, created_at_ms DESC",
    )
    suspend fun getSelectedConversations(runId: String): List<ProfileCorpusConversationEntity>

    @Query("SELECT * FROM profile_corpus_messages WHERE conversation_id = :conversationId ORDER BY order_index ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<ProfileCorpusMessageEntity>

    @Query("SELECT COUNT(*) FROM profile_corpus_conversations WHERE corpus_run_id = :runId")
    suspend fun getConversationCount(runId: String): Int

    @Query("SELECT SUM(user_token_count) FROM profile_corpus_conversations WHERE corpus_run_id = :runId AND selected_for_analysis = 1")
    suspend fun getTotalSelectedUserTokens(runId: String): Int?

    @Update
    suspend fun updateConversation(entity: ProfileCorpusConversationEntity)

    @Query("UPDATE profile_corpus_conversations SET signal_score = :score WHERE id = :id")
    suspend fun updateSignalScore(id: String, score: Float)

    @Query("UPDATE profile_corpus_conversations SET cluster_id = :clusterId WHERE id = :id")
    suspend fun updateClusterId(id: String, clusterId: Int)

    @Query("UPDATE profile_corpus_conversations SET selected_for_analysis = :selected WHERE id = :id")
    suspend fun updateSelected(id: String, selected: Boolean)

    @Query("DELETE FROM profile_corpus_conversations WHERE corpus_run_id = :runId")
    suspend fun deleteConversationsForRun(runId: String)

    @Query("DELETE FROM profile_corpus_messages WHERE corpus_run_id = :runId")
    suspend fun deleteMessagesForRun(runId: String)

    @Query(
        """
        SELECT DISTINCT c.* FROM profile_corpus_conversations c
        LEFT JOIN profile_corpus_messages m ON m.conversation_id = c.id AND m.role = 'USER'
        WHERE c.corpus_run_id = :runId AND c.selected_for_analysis = 1
          AND (c.title LIKE :query OR m.content LIKE :query)
        ORDER BY c.signal_score DESC
        LIMIT :limit
        """,
    )
    suspend fun searchSelectedConversations(
        runId: String,
        query: String,
        limit: Int,
    ): List<ProfileCorpusConversationEntity>

    @Query(
        "SELECT * FROM profile_corpus_messages WHERE conversation_id = :conversationId " +
            "ORDER BY order_index ASC LIMIT :pageSize OFFSET :offset",
    )
    suspend fun getMessagesPage(
        conversationId: String,
        pageSize: Int,
        offset: Int,
    ): List<ProfileCorpusMessageEntity>
}
