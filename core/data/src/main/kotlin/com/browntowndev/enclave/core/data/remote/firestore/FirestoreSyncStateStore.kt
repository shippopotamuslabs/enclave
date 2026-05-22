package com.browntowndev.enclave.core.data.remote.firestore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists per-user `lastAppliedSequence`, a stable local `deviceId`, and helpers for
 * generating client mutation IDs / timestamps used when uploading entity writes.
 *
 * Production uses DataStore (the `cloud_sync_*` keys defined below). The no-arg
 * fallback retains an in-memory map so unit tests that don't need persistence — and
 * subclass overrides — can still construct the store without an Android context.
 *
 * Persisted keys (per spec FEAT-cloud-sync-event-feed §"DataStore Schema"):
 *  - `cloud_sync_device_id`
 *  - `cloud_sync_last_applied_sequence_{uid}`
 *  - `cloud_sync_feed_schema_version_{uid}`
 */
internal open class FirestoreSyncStateStore(
    private val dataStore: DataStore<Preferences>? = null,
) {
    private val lastAppliedFallback = ConcurrentHashMap<String, Long>()
    private val deviceIdFallback: String by lazy { UUID.randomUUID().toString() }

    open suspend fun lastAppliedSequence(userId: String): Long {
        val store = dataStore ?: return lastAppliedFallback[userId] ?: 0L
        return store.data.first()[lastAppliedSequenceKey(userId)] ?: 0L
    }

    open suspend fun setLastAppliedSequence(
        userId: String,
        sequence: Long,
    ) {
        val store = dataStore
        if (store == null) {
            lastAppliedFallback[userId] = sequence
            return
        }
        store.edit { prefs -> prefs[lastAppliedSequenceKey(userId)] = sequence }
    }

    open suspend fun feedSchemaVersion(userId: String): Long {
        val store = dataStore ?: return 0L
        return store.data.first()[feedSchemaVersionKey(userId)] ?: 0L
    }

    open suspend fun setFeedSchemaVersion(
        userId: String,
        version: Long,
    ) {
        val store = dataStore ?: return
        store.edit { prefs -> prefs[feedSchemaVersionKey(userId)] = version }
    }

    open suspend fun deviceId(): String {
        val store = dataStore ?: return deviceIdFallback
        val existing = store.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        store.edit { prefs ->
            // Re-check inside edit to avoid two cold-start callers racing to a different id.
            val concurrent = prefs[DEVICE_ID_KEY]
            if (concurrent == null) {
                prefs[DEVICE_ID_KEY] = generated
            }
        }
        return store.data.first()[DEVICE_ID_KEY] ?: generated
    }

    open fun newClientMutationId(): String = UUID.randomUUID().toString()

    open fun currentTimeMillis(): Long = System.currentTimeMillis()

    private fun lastAppliedSequenceKey(userId: String) =
        longPreferencesKey("cloud_sync_last_applied_sequence_$userId")

    private fun feedSchemaVersionKey(userId: String) =
        longPreferencesKey("cloud_sync_feed_schema_version_$userId")

    private companion object {
        private val DEVICE_ID_KEY = stringPreferencesKey("cloud_sync_device_id")
    }
}
