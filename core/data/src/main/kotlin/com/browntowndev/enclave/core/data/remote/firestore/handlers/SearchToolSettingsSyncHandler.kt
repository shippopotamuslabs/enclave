package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.repository.SearchToolSettingsRecord
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.first

/**
 * `EntitySyncAdapter<SearchToolSettingsRecord>` mirroring
 * [ApiKeySyncHandler] for the `search_tool_settings` singleton.
 *
 * The queue is the source of truth for both the "pending" and "claimed" states — this
 * handler never stores per-instance claim flags. The engine's two-phase claim
 * (`syncAllLocalDataToCloud` then `syncPendingChangesInternal`) is therefore safe: a
 * second `claimPending` is a no-op for the captured `updatedAt` because the queue stores
 * it in a separate key. `getClaimedForSync` is read-only; the claim is released by
 * `markSynced` after the round-trip succeeds.
 *
 * Remote-apply honors the local-pending-edit-wins policy: if the user has a pending
 * upload and the caller did not request an explicit overwrite, the remote snapshot is
 * dropped to avoid clobbering the newer local edit.
 */
internal class SearchToolSettingsSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<SearchToolSettingsRecord> {
    override val collectionPath: String = "search_tool_settings"

    override suspend fun claimPending() {
        // The queue's claimPending is idempotent: the second call (from
        // syncAllLocalDataToCloud followed by syncPendingChangesInternal) is a no-op
        // for the captured claim because PENDING is already false. The captured
        // `updatedAt` survives in the queue's separate CLAIMED_AT key.
        ctx.searchToolSettingsSyncQueue?.claimPending()
    }

    override suspend fun getClaimedForSync(): List<SearchToolSettingsRecord> {
        val updatedAt = ctx.searchToolSettingsSyncQueue?.getClaimedAtMillis() ?: return emptyList()
        val repository = ctx.settingsRepository ?: return emptyList()
        val settings = repository.settingsFlow.first()
        return listOf(
            SearchToolSettingsRecord(
                searchEnabled = settings.searchEnabled,
                xSearchEnabled = settings.xSearchEnabled,
                searchProvider = settings.searchProvider,
                updatedAt = updatedAt,
            ),
        )
    }

    override suspend fun markSynced(entities: List<SearchToolSettingsRecord>) {
        ctx.searchToolSettingsSyncQueue?.clearClaimedSync()
    }

    override fun getEntityId(entity: SearchToolSettingsRecord): String = "main"

    override fun shouldDelete(entity: SearchToolSettingsRecord): Boolean = false

    override suspend fun toFirestore(local: SearchToolSettingsRecord): Map<String, Any?> =
        mapOf(
            "search_enabled" to local.searchEnabled,
            "x_search_enabled" to local.xSearchEnabled,
            "search_provider" to local.searchProvider.name,
            "updated_at" to local.updatedAt,
        )

    override fun fromFirestore(doc: DocumentSnapshot): SearchToolSettingsRecord? {
        val searchEnabled = doc.getBoolean("search_enabled")
        val xSearchEnabled = doc.getBoolean("x_search_enabled")
        val searchProviderName = doc.getString("search_provider")
        val updatedAt = doc.getLong("updated_at")
        if (anyRequiredFieldMissing(searchEnabled, xSearchEnabled, searchProviderName, updatedAt)) {
            return null
        }
        val provider = parseProvider(searchProviderName!!) ?: return null
        return SearchToolSettingsRecord(
            searchEnabled = searchEnabled!!,
            xSearchEnabled = xSearchEnabled!!,
            searchProvider = provider,
            updatedAt = updatedAt!!,
        )
    }

    private fun anyRequiredFieldMissing(
        searchEnabled: Boolean?,
        xSearchEnabled: Boolean?,
        searchProviderName: String?,
        updatedAt: Long?,
    ): Boolean =
        searchEnabled == null ||
            xSearchEnabled == null ||
            searchProviderName == null ||
            updatedAt == null

    private fun parseProvider(name: String): SearchProvider? =
        try {
            SearchProvider.valueOf(name)
        } catch (_: IllegalArgumentException) {
            null
        }

    override suspend fun upsertLocal(
        entity: SearchToolSettingsRecord,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    ) {
        // Local-pending-edit-wins: if the user has an in-flight upload (either
        // pending or already claimed) and the caller did not explicitly request
        // an overwrite, drop the remote snapshot. The `hasInFlightLocalEdit`
        // check covers both the pre-claim and the in-flight (claimed) states so
        // a stale remote echo arriving after `claimPending` still loses to the
        // newer local edit.
        if (!overwrite && ctx.searchToolSettingsSyncQueue?.hasInFlightLocalEdit() == true) return
        ctx.settingsRepository?.applyRemoteSearchToolSettings(entity, overwrite)
    }

    override suspend fun deleteLocal(id: String) = Unit // never delete the singleton
}
