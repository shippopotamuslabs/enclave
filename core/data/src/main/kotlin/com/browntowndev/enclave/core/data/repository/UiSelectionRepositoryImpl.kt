package com.browntowndev.enclave.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.port.repository.UiSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore-backed implementation of [UiSelectionRepository]. Stores the user's last-selected
 * chat mode and studio media type as string preference keys inside the shared `"settings"`
 * Preferences DataStore. Unknown/corrupted values fall back to the domain defaults.
 */
class UiSelectionRepositoryImpl
    @Inject
    internal constructor(
        private val dataStore: DataStore<Preferences>,
    ) : UiSelectionRepository {
        companion object {
            private val CHAT_MODE_KEY = stringPreferencesKey("ui_selected_chat_mode")
            private val STUDIO_MEDIA_TYPE_KEY = stringPreferencesKey("ui_selected_studio_media_type")
        }

        override val selectedChatMode: Flow<Mode> =
            dataStore.data.map { preferences ->
                preferences[CHAT_MODE_KEY]?.let { name ->
                    runCatching { Mode.valueOf(name) }.getOrNull()
                } ?: Mode.DEFAULT
            }

        override val selectedStudioMediaType: Flow<MediaCapability> =
            dataStore.data.map { preferences ->
                preferences[STUDIO_MEDIA_TYPE_KEY]?.let { name ->
                    runCatching { MediaCapability.valueOf(name) }.getOrNull()
                } ?: MediaCapability.DEFAULT
            }

        override suspend fun setChatMode(mode: Mode) {
            dataStore.edit { preferences ->
                preferences[CHAT_MODE_KEY] = mode.name
            }
        }

        override suspend fun setStudioMediaType(type: MediaCapability) {
            dataStore.edit { preferences ->
                preferences[STUDIO_MEDIA_TYPE_KEY] = type.name
            }
        }
    }
