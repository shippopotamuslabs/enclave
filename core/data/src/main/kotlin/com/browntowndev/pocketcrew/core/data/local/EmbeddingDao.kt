package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface EmbeddingDao {
    @RawQuery
    suspend fun searchSimilarMessages(query: SupportSQLiteQuery): List<String>

    @RawQuery
    suspend fun insertEmbedding(query: SupportSQLiteQuery): Long
}
