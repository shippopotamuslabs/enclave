package com.browntowndev.enclave.core.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalVaultMetadataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : VaultMetadataPort {
        override suspend fun getVaultSalt(): String? = dataStore.data.first()[VAULT_SALT_KEY]

        override suspend fun saveVaultSalt(salt: String) {
            dataStore.edit { preferences ->
                preferences[VAULT_SALT_KEY] = salt
            }
        }

        override suspend fun getVaultVerificationData(): String? = dataStore.data.first()[VAULT_VERIFICATION_KEY]

        override suspend fun saveVaultVerificationData(ciphertext: String?) {
            dataStore.edit { preferences ->
                if (ciphertext == null) {
                    preferences.remove(VAULT_VERIFICATION_KEY)
                } else {
                    preferences[VAULT_VERIFICATION_KEY] = ciphertext
                }
            }
        }

        override suspend fun getWrappedDek(): String? = dataStore.data.first()[VAULT_WRAPPED_DEK_KEY]

        override suspend fun saveWrappedDek(wrappedDek: String?) {
            dataStore.edit { preferences ->
                if (wrappedDek == null) {
                    preferences.remove(VAULT_WRAPPED_DEK_KEY)
                } else {
                    preferences[VAULT_WRAPPED_DEK_KEY] = wrappedDek
                }
            }
        }

        companion object {
            private val VAULT_SALT_KEY = stringPreferencesKey("vault_local_salt")
            private val VAULT_VERIFICATION_KEY = stringPreferencesKey("vault_local_verification")
            private val VAULT_WRAPPED_DEK_KEY = stringPreferencesKey("vault_local_wrapped_dek")
        }
    }
