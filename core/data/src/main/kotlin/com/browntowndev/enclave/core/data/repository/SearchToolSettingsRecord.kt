package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.settings.SearchProvider

/**
 * Internal sync payload for the `search_tool_settings` Firestore collection.
 *
 * The four fields are the entire spec; this is a value-only carrier.
 * The implementation of `markPendingSync` / `claimPending` / `clearClaimedSync` /
 * `applyRemoteSearchToolSettings` lives in [SearchToolSettingsSyncQueue] and
 * [SettingsRepositoryImpl], not on this type.
 */
internal data class SearchToolSettingsRecord(
    val searchEnabled: Boolean,
    val xSearchEnabled: Boolean,
    val searchProvider: SearchProvider,
    val updatedAt: Long,
)
