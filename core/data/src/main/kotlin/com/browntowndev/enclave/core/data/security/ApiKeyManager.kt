package com.browntowndev.enclave.core.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

open class ApiKeyManager
    @VisibleForTesting
    internal constructor(
        private val prefsProvider: () -> SharedPreferences,
        private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        private val syncScope: CoroutineScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
    ) {
        companion object {
            const val TAVILY_SEARCH_ALIAS = "tavily_web_search"
            const val EXA_SEARCH_ALIAS = "exa_web_search"
            const val X_SEARCH_ALIAS = "x_search"

            private val PENDING_SYNC_KEY =
                androidx.datastore.preferences.core
                    .stringSetPreferencesKey("byok_api_keys_pending_sync")
            private val PENDING_DELETE_KEY =
                androidx.datastore.preferences.core
                    .stringSetPreferencesKey("byok_api_keys_pending_delete")
            private val CLAIMED_SYNC_KEY =
                androidx.datastore.preferences.core
                    .stringSetPreferencesKey("byok_api_keys_claimed_sync")
            private val CLAIMED_DELETE_KEY =
                androidx.datastore.preferences.core
                    .stringSetPreferencesKey("byok_api_keys_claimed_delete")
        }

        @Inject
        constructor(
            @ApplicationContext context: Context,
            dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        ) : this({
            EncryptedSharedPreferences.create(
                context,
                "byok_api_keys",
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }, dataStore)

        private val prefs: SharedPreferences by lazy { prefsProvider() }

        fun save(
            credentialAlias: String,
            apiKey: String,
        ) {
            val normalizedAlias = normalizedAlias(credentialAlias) ?: return
            val normalizedKey = apiKey.trim()
            if (normalizedKey.isEmpty()) {
                delete(normalizedAlias)
                return
            }
            prefs.edit().putString(normalizedAlias, normalizedKey).commit()
            markPendingSync(normalizedAlias)
        }

        open fun get(credentialAlias: String): String? = normalizedAlias(credentialAlias)?.let { prefs.getString(it, null) }

        fun has(credentialAlias: String): Boolean = !get(credentialAlias).isNullOrBlank()

        fun delete(credentialAlias: String) {
            val normalizedAlias = normalizedAlias(credentialAlias) ?: return
            prefs.edit().remove(normalizedAlias).commit()
            markPendingDelete(normalizedAlias)
        }

        private fun normalizedAlias(alias: String): String? = alias.trim().takeIf { it.isNotEmpty() }

        private fun markPendingSync(alias: String) {
            syncScope.launch {
                dataStore.edit { preferences ->
                    val current = preferences[PENDING_SYNC_KEY] ?: emptySet()
                    preferences[PENDING_SYNC_KEY] = current + alias
                    // Remove from pending delete and claimed keys if it was there
                    val currentDelete = preferences[PENDING_DELETE_KEY] ?: emptySet()
                    preferences[PENDING_DELETE_KEY] = currentDelete - alias

                    val claimedSync = preferences[CLAIMED_SYNC_KEY] ?: emptySet()
                    preferences[CLAIMED_SYNC_KEY] = claimedSync - alias
                    val claimedDelete = preferences[CLAIMED_DELETE_KEY] ?: emptySet()
                    preferences[CLAIMED_DELETE_KEY] = claimedDelete - alias
                }
            }
        }

        private fun markPendingDelete(alias: String) {
            syncScope.launch {
                dataStore.edit { preferences ->
                    val current = preferences[PENDING_DELETE_KEY] ?: emptySet()
                    preferences[PENDING_DELETE_KEY] = current + alias
                    // Remove from pending sync and claimed keys if it was there
                    val currentSync = preferences[PENDING_SYNC_KEY] ?: emptySet()
                    preferences[PENDING_SYNC_KEY] = currentSync - alias

                    val claimedSync = preferences[CLAIMED_SYNC_KEY] ?: emptySet()
                    preferences[CLAIMED_SYNC_KEY] = claimedSync - alias
                    val claimedDelete = preferences[CLAIMED_DELETE_KEY] ?: emptySet()
                    preferences[CLAIMED_DELETE_KEY] = claimedDelete - alias
                }
            }
        }

        suspend fun claimPending() {
            dataStore.edit { preferences ->
                val pendingSync = preferences[PENDING_SYNC_KEY] ?: emptySet()
                val pendingDelete = preferences[PENDING_DELETE_KEY] ?: emptySet()

                val claimedSync = preferences[CLAIMED_SYNC_KEY] ?: emptySet()
                val claimedDelete = preferences[CLAIMED_DELETE_KEY] ?: emptySet()

                preferences[CLAIMED_SYNC_KEY] = claimedSync + pendingSync
                preferences[CLAIMED_DELETE_KEY] = claimedDelete + pendingDelete

                preferences[PENDING_SYNC_KEY] = emptySet()
                preferences[PENDING_DELETE_KEY] = emptySet()
            }
        }

        open suspend fun getClaimedSyncAliases(): Set<String> = dataStore.data.first()[CLAIMED_SYNC_KEY] ?: emptySet()

        open suspend fun getClaimedDeleteAliases(): Set<String> = dataStore.data.first()[CLAIMED_DELETE_KEY] ?: emptySet()

        open suspend fun clearClaimedSync(aliases: Collection<String>) {
            dataStore.edit { preferences ->
                val current = preferences[CLAIMED_SYNC_KEY] ?: emptySet()
                preferences[CLAIMED_SYNC_KEY] = current - aliases.toSet()
            }
        }

        open suspend fun clearClaimedDelete(aliases: Collection<String>) {
            dataStore.edit { preferences ->
                val current = preferences[CLAIMED_DELETE_KEY] ?: emptySet()
                preferences[CLAIMED_DELETE_KEY] = current - aliases.toSet()
            }
        }

        suspend fun clearAllPending() {
            dataStore.edit { preferences ->
                preferences.remove(PENDING_SYNC_KEY)
                preferences.remove(PENDING_DELETE_KEY)
                preferences.remove(CLAIMED_SYNC_KEY)
                preferences.remove(CLAIMED_DELETE_KEY)
            }
        }

        /**
         * Internal save for sync to avoid loop
         */
        internal fun saveFromRemote(
            credentialAlias: String,
            apiKey: String,
        ) {
            val normalizedAlias = normalizedAlias(credentialAlias) ?: return
            prefs.edit().putString(normalizedAlias, apiKey.trim()).commit()
        }

        internal fun deleteFromRemote(credentialAlias: String) {
            val normalizedAlias = normalizedAlias(credentialAlias) ?: return
            prefs.edit().remove(normalizedAlias).commit()
        }
    }
