package com.browntowndev.pocketcrew.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.port.repository.PipelineStateRepository
import com.browntowndev.pocketcrew.domain.qualifier.PipelineDataStore
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of PipelineStateRepository.
 * Uses a dedicated DataStore with @PipelineDataStore qualifier to avoid conflicts.
 */
@Singleton
class PipelineStateRepositoryImpl @Inject constructor(
    @PipelineDataStore private val dataStore: DataStore<Preferences>
) : PipelineStateRepository {
    
    companion object {
        private fun pipelineStateKey(chatId: String) = stringPreferencesKey("pipeline_state_$chatId")
    }
    
    override suspend fun persistPipelineState(chatId: String, state: PipelineState) {
        val key = pipelineStateKey(chatId)
        val serialized = state.toJson()
        
        dataStore.edit { preferences ->
            preferences[key] = serialized
        }
    }
    
    override suspend fun getPipelineState(chatId: String): PipelineState? {
        val key = pipelineStateKey(chatId)
        val preferences = dataStore.data.firstOrNull() ?: return null
        
        val serialized = preferences[key] ?: return null
        
        return try {
            PipelineState.fromJson(serialized)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun clearPipelineState(chatId: String) {
        val key = pipelineStateKey(chatId)
        
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }
}
