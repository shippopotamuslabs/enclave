package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProfileFindingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProfileFindingsEntity): Long

    @Query("SELECT * FROM profile_corpus_findings WHERE corpus_run_id = :runId ORDER BY created_at_ms ASC")
    suspend fun getAll(runId: String): List<ProfileFindingsEntity>

    @Query("SELECT * FROM profile_corpus_findings WHERE corpus_run_id = :runId AND category = :category ORDER BY created_at_ms ASC")
    suspend fun getByCategory(runId: String, category: String): List<ProfileFindingsEntity>

    @Query("DELETE FROM profile_corpus_findings WHERE corpus_run_id = :runId")
    suspend fun deleteAll(runId: String)
}
