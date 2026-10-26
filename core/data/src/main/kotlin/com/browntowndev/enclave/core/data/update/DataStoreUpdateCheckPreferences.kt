package com.browntowndev.enclave.core.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.browntowndev.enclave.domain.port.update.UpdateCheckPreferencesPort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UpdateCheckPreferencesPort] over the shared settings DataStore (ENC-250).
 * No Room involvement (schema lock).
 */
@Singleton
class DataStoreUpdateCheckPreferences
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : UpdateCheckPreferencesPort {
        companion object {
            private val LAST_CHECK_MILLIS_KEY = longPreferencesKey("update_last_check_millis")
            private val DISMISSED_VERSION_CODE_KEY = intPreferencesKey("update_dismissed_version_code")
        }

        override suspend fun lastCheckMillis(): Long =
            dataStore.data.map { preferences -> preferences[LAST_CHECK_MILLIS_KEY] ?: 0L }.first()

        override suspend fun setLastCheckMillis(value: Long) {
            dataStore.edit { preferences ->
                preferences[LAST_CHECK_MILLIS_KEY] = value
            }
        }

        override suspend fun dismissedVersionCode(): Int =
            dataStore.data.map { preferences -> preferences[DISMISSED_VERSION_CODE_KEY] ?: 0 }.first()

        override suspend fun setDismissedVersionCode(value: Int) {
            dataStore.edit { preferences ->
                preferences[DISMISSED_VERSION_CODE_KEY] = value
            }
        }
    }
