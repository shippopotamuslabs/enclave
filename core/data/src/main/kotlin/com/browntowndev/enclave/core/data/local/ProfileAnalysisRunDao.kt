package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProfileAnalysisRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProfileAnalysisRunEntity)

    @Query("SELECT * FROM profile_analysis_runs WHERE corpus_run_id = :runId LIMIT 1")
    suspend fun getByRunId(runId: String): ProfileAnalysisRunEntity?

    @Query("DELETE FROM profile_analysis_runs WHERE corpus_run_id = :runId")
    suspend fun delete(runId: String)
}
