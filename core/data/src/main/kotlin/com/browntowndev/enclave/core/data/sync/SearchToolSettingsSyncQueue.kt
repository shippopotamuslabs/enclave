package com.browntowndev.enclave.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Internal DataStore-backed state machine for the `search_tool_settings` collection.
 *
 * Tracks two independent values so the engine's two-phase claim (`syncAllLocalDataToCloud`
 * followed by `syncPendingChangesInternal` — both call `claimPending` on every adapter) is
 * idempotent:
 * - [PENDING_SYNC_KEY] — the user has touched a setting locally; the next pending-sync
 *   cycle must upload it. Cleared by [claimPending] when the claim is captured.
 * - [CLAIMED_AT_KEY] — the timestamp captured at claim time; read by the handler's
 *   `getClaimedForSync` to assemble the upload payload. Cleared by [clearClaimedSync]
 *   after the round-trip succeeds.
 *
 * Because the two values are stored in separate keys, calling [claimPending] a second time
 * after the first is a no-op for the CLAIMED_AT value (PENDING is already false) and the
 * handler can safely call `claimPending` from both `syncAllLocalDataToCloud` and
 * `syncPendingChangesInternal` without losing the captured claim.
 */
@javax.inject.Singleton
class SearchToolSettingsSyncQueue
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        @ApplicationScope @Suppress("unused") private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) {
        suspend fun markPendingSync() {
            dataStore.edit { preferences ->
                preferences[PENDING_SYNC_KEY] = System.currentTimeMillis()
            }
        }

        /**
         * Capture the claim if a pending edit exists. Returns `true` if the claim was
         * captured (PENDING was set before this call). Always clears [PENDING_SYNC_KEY]
         * because once a claim has been recorded the queue is no longer pending. Safe to
         * call multiple times — repeated calls simply return `false` while leaving the
         * already-captured claim in [CLAIMED_AT_KEY] untouched.
         */
        suspend fun claimPending(): Boolean {
            var wasPending = false
            dataStore.edit { preferences ->
                val pending = preferences[PENDING_SYNC_KEY]
                if (pending != null) {
                    // Move the timestamp into CLAIMED_AT so the handler can read it on
                    // `getClaimedForSync`. This survives the subsequent PENDING clear and
                    // any later (idempotent) `claimPending` calls.
                    preferences[CLAIMED_AT_KEY] = pending
                    preferences.remove(PENDING_SYNC_KEY)
                    wasPending = true
                }
            }
            return wasPending
        }

        /**
         * Returns the wall-clock millis captured at the last successful [claimPending],
         * or `null` if no claim is in flight. Read-only — callers must not depend on
         * this to clear state. Use [clearClaimedSync] to release the claim.
         */
        suspend fun getClaimedAtMillis(): Long? = dataStore.data.first()[CLAIMED_AT_KEY]

        /**
         * Releases the claim captured by [claimPending]. Idempotent and safe to call
         * without a preceding claim. Does not touch [PENDING_SYNC_KEY], so a mark that
         * landed after the claim will still be picked up by the next cycle.
         */
        suspend fun clearClaimedSync() {
            dataStore.edit { preferences ->
                preferences.remove(CLAIMED_AT_KEY)
            }
        }

        suspend fun isPending(): Boolean = dataStore.data.first()[PENDING_SYNC_KEY] != null

        /**
         * True when the user has a local edit in flight — either queued via
         * [markPendingSync] (PENDING is set) or already captured by [claimPending]
         * (CLAIMED_AT is set, awaiting a successful write to Firestore). The
         * remote-apply guard uses this so a stale remote snapshot cannot clobber
         * a newer local edit that has been claimed but not yet round-tripped.
         */
        suspend fun hasInFlightLocalEdit(): Boolean {
            val prefs = dataStore.data.first()
            return prefs[PENDING_SYNC_KEY] != null || prefs[CLAIMED_AT_KEY] != null
        }

        private companion object {
            private val PENDING_SYNC_KEY = longPreferencesKey("search_tool_settings_pending_sync")
            private val CLAIMED_AT_KEY = longPreferencesKey("search_tool_settings_claimed_at")
        }
    }
