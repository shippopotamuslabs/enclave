package com.browntowndev.enclave.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.domain.qualifier.StudioActiveGenerationDataStore
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveStudioGenerationPersistence
    @Inject
    constructor(
        @param:StudioActiveGenerationDataStore private val dataStore: DataStore<Preferences>,
    ) {
        private val activeGenerationKey = stringPreferencesKey("studio_active_generation")

        suspend fun persist(generation: ActiveStudioGeneration) {
            dataStore.edit { preferences ->
                preferences[activeGenerationKey] = ActiveStudioGenerationCodec.encode(generation)
            }
        }

        suspend fun clear() {
            dataStore.edit { preferences ->
                preferences.remove(activeGenerationKey)
            }
        }

        suspend fun load(): ActiveStudioGeneration? {
            val raw = dataStore.data.firstOrNull()?.get(activeGenerationKey) ?: return null
            return ActiveStudioGenerationCodec.decode(raw)
        }
    }
