@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (DocumentSnapshot), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore.handlers

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.testApiKeyManager
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.core.data.repository.SearchToolSettingsRecord
import com.browntowndev.enclave.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Coverage for `SearchToolSettingsSyncHandler` against the contract in
 * `plans/search-tool-enable-sync/test_spec.md`. Each test drives the public API and
 * asserts the behavioural contract — round-trip payload shape, remote-apply seam, and
 * the local-pending-edit-wins policy.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class SearchToolSettingsSyncHandlerTest {
    @TempDir
    lateinit var tempDir: Path

    // -----------------------------------------------------------------------
    // Round-trip scenarios — handler ↔ Firestore payload shape
    // -----------------------------------------------------------------------

    @Test
    fun `S-ROUNDTRIP-001 getClaimedForSync returns a single SearchToolSettingsRecord when pending`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository =
                createSettingsRepository(
                    dataStore = dataStore,
                    initialSearchEnabled = true,
                    initialXSearchEnabled = false,
                    initialProvider = SearchProvider.TAVILY,
                )
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)

            queue.markPendingSync()
            handler.claimPending()
            val claimed = handler.getClaimedForSync()

            assertEquals(1, claimed.size, "exactly one record must be returned when the queue was pending")
            val record = claimed.first()
            assertTrue(record.searchEnabled, "searchEnabled must mirror the repository's settingsFlow")
            assertFalse(record.xSearchEnabled, "xSearchEnabled must mirror the repository's settingsFlow")
            assertEquals(SearchProvider.TAVILY, record.searchProvider, "searchProvider must mirror the repository's settingsFlow")
            assertTrue(
                record.updatedAt >= 0L,
                "updatedAt must be a Long >= 0 (queue-mark-time clock)",
            )
        }

    @Test
    fun `S-ROUNDTRIP-002 getClaimedForSync returns empty when nothing pending`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository = createSettingsRepository(dataStore = dataStore)
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)

            // `EntitySyncAdapter.claimPending` is Unit-returning (the 20 existing
            // handlers all rely on that signature). The handler internally tracks
            // the queue's claim result so `getClaimedForSync` can short-circuit.
            handler.claimPending()
            val list = handler.getClaimedForSync()

            assertEquals(
                emptyList<SearchToolSettingsRecord>(),
                list,
                "getClaimedForSync() must return an empty list when nothing was claimed",
            )
            assertFalse(
                queue.isPending(),
                "queue must remain empty when no markPendingSync was called",
            )
        }

    @Test
    fun `S-ROUNDTRIP-003 toFirestore writes the four user-facing fields`() =
        runTest {
            val queue = createQueue(backgroundScope)
            val ctx = createContext(queue = queue)
            val handler = SearchToolSettingsSyncHandler(ctx)
            val record =
                SearchToolSettingsRecord(
                    searchEnabled = true,
                    xSearchEnabled = true,
                    searchProvider = SearchProvider.EXA,
                    updatedAt = 1700000000000L,
                )

            val payload = handler.toFirestore(record)

            assertEquals(
                mapOf(
                    "search_enabled" to true,
                    "x_search_enabled" to true,
                    "search_provider" to "EXA",
                    "updated_at" to 1700000000000L,
                ),
                payload,
                "toFirestore() must produce exactly the four user-facing fields with no metadata",
            )
        }

    // -----------------------------------------------------------------------
    // Remote-apply scenarios — fromFirestore / upsertLocal seams
    // -----------------------------------------------------------------------

    @Test
    fun `S-REMOTEAPPLY-001 fromFirestore decodes a complete document`() =
        runTest {
            val queue = createQueue(backgroundScope)
            val ctx = createContext(queue = queue)
            val handler = SearchToolSettingsSyncHandler(ctx)
            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to true,
                            "x_search_enabled" to false,
                            "search_provider" to "TAVILY",
                            "updated_at" to 1700000000000L,
                        ),
                )

            val record = handler.fromFirestore(doc)

            assertEquals(
                SearchToolSettingsRecord(
                    searchEnabled = true,
                    xSearchEnabled = false,
                    searchProvider = SearchProvider.TAVILY,
                    updatedAt = 1700000000000L,
                ),
                record,
            )
        }

    @Test
    fun `S-REMOTEAPPLY-002 upsertLocal writes all three flags and propagates via settingsFlow`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository = createSettingsRepository(dataStore = dataStore)
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)
            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to true,
                            "x_search_enabled" to true,
                            "search_provider" to "EXA",
                            "updated_at" to 1700000000000L,
                        ),
                )
            val record =
                SearchToolSettingsRecord(
                    searchEnabled = true,
                    xSearchEnabled = true,
                    searchProvider = SearchProvider.EXA,
                    updatedAt = 1700000000000L,
                )

            handler.upsertLocal(record, doc, overwrite = true)

            val settings = repository.settingsFlow.first()
            assertTrue(settings.searchEnabled, "searchEnabled must be true after remote apply")
            assertTrue(settings.xSearchEnabled, "xSearchEnabled must be true after remote apply")
            assertEquals(SearchProvider.EXA, settings.searchProvider, "searchProvider must be EXA after remote apply")
            assertFalse(
                queue.isPending(),
                "a remote apply must NOT enqueue a follow-up upload",
            )
        }

    @Test
    fun `S-REMOTEAPPLY-003 upsertLocal with overwrite=false respects local pending edit`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository = createSettingsRepository(dataStore = dataStore)
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)

            // user has just disabled search and the queue is pending
            repository.updateSearchEnabled(false)
            queue.markPendingSync()

            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to true,
                            "x_search_enabled" to false,
                            "search_provider" to "TAVILY",
                            "updated_at" to 1700000000000L,
                        ),
                )
            val record =
                SearchToolSettingsRecord(
                    searchEnabled = true,
                    xSearchEnabled = false,
                    searchProvider = SearchProvider.TAVILY,
                    updatedAt = 1700000000000L,
                )

            handler.upsertLocal(record, doc, overwrite = false)

            assertFalse(
                repository.settingsFlow.first().searchEnabled,
                "local pending edit wins — searchEnabled must remain false",
            )
            assertTrue(
                queue.isPending(),
                "the queue must still be pending for the next write",
            )
        }

    @Test
    fun `S-REMOTEAPPLY-004 fromFirestore returns null when search_provider is missing`() =
        runTest {
            val queue = createQueue(backgroundScope)
            val ctx = createContext(queue = queue)
            val handler = SearchToolSettingsSyncHandler(ctx)
            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to true,
                            "x_search_enabled" to false,
                            // search_provider intentionally absent
                            "updated_at" to 1700000000000L,
                        ),
                )

            assertNull(
                handler.fromFirestore(doc),
                "fromFirestore() must return null when search_provider is missing",
            )
        }

    @Test
    fun `S-REMOTEAPPLY-005 in-flight claimed local edit is not clobbered by stale remote echo`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository =
                createSettingsRepository(
                    dataStore = dataStore,
                    initialSearchEnabled = false,
                )
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)

            // user has just disabled search and the queue has been claimed (i.e.
            // the upload is in flight, PENDING is cleared but CLAIMED_AT is set).
            repository.updateSearchEnabled(false)
            queue.markPendingSync()
            handler.claimPending()
            // sanity: PENDING is cleared (the engine already captured the claim),
            // but the queue still has an in-flight edit (CLAIMED_AT is set).
            assertFalse(
                queue.isPending(),
                "PENDING must be cleared by claimPending() — the engine is already uploading",
            )
            assertTrue(
                queue.hasInFlightLocalEdit(),
                "CLAIMED_AT must still be set — the local edit is in flight",
            )

            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to true, // stale — the user just disabled search
                            "x_search_enabled" to false,
                            "search_provider" to "TAVILY",
                            "updated_at" to 1500000000000L, // older than the local edit
                        ),
                )
            val record =
                SearchToolSettingsRecord(
                    searchEnabled = true,
                    xSearchEnabled = false,
                    searchProvider = SearchProvider.TAVILY,
                    updatedAt = 1500000000000L,
                )

            handler.upsertLocal(record, doc, overwrite = false)

            assertFalse(
                repository.settingsFlow.first().searchEnabled,
                "in-flight claimed local edit must not be clobbered by a stale remote echo",
            )
            assertTrue(
                queue.hasInFlightLocalEdit(),
                "the queue must still report an in-flight edit (CLAIMED_AT remains set)",
            )
        }

    // -----------------------------------------------------------------------
    // Echo / mutation defense
    // -----------------------------------------------------------------------

    @Test
    fun `S-ECHO-001 same-device echo does not flip the local value`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository =
                createSettingsRepository(
                    dataStore = dataStore,
                    initialSearchEnabled = true,
                )
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)

            // Local state: searchEnabled = true, queue is pending (we just uploaded)
            repository.updateSearchEnabled(true)
            queue.markPendingSync()

            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to false, // this is the echo of our own write
                            "x_search_enabled" to false,
                            "search_provider" to "TAVILY",
                            "updated_at" to 1700000000000L,
                        ),
                )
            val echoRecord =
                SearchToolSettingsRecord(
                    searchEnabled = false,
                    xSearchEnabled = false,
                    searchProvider = SearchProvider.TAVILY,
                    updatedAt = 1700000000000L,
                )

            // eventDeviceId == localDeviceId (the echo of our own write arriving back)
            handler.upsertLocal(echoRecord, doc, overwrite = false)

            assertTrue(
                repository.settingsFlow.first().searchEnabled,
                "searchEnabled must remain true when the echo is from our own device",
            )
            assertTrue(
                queue.isPending(),
                "the queue must still be pending for the next write",
            )
        }

    @Test
    fun `S-MUTATION-001 handler refuses to default missing provider`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository =
                createSettingsRepository(
                    dataStore = dataStore,
                    initialProvider = SearchProvider.EXA,
                )
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)
            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to true,
                            "x_search_enabled" to true,
                            // search_provider intentionally absent
                            "updated_at" to 1700000000000L,
                        ),
                )

            val record = handler.fromFirestore(doc)
            assertNull(record, "fromFirestore() must return null when search_provider is missing")

            // The local searchProvider must NOT be silently flipped back to TAVILY
            // (the lazy implementation that defaults the provider would fail this assertion).
            assertEquals(
                SearchProvider.EXA,
                repository.settingsFlow.first().searchProvider,
                "the local searchProvider must remain EXA — no silent default to TAVILY",
            )
        }

    @Test
    fun `S-MUTATION-002 clear-key-with-search-enabled-true does not disable search`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository =
                createSettingsRepository(
                    dataStore = dataStore,
                    initialSearchEnabled = true,
                    initialTavilyKeyPresent = true,
                )
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)

            // local key is cleared
            repository.clearTavilyApiKey()

            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to true,
                            "x_search_enabled" to false,
                            "search_provider" to "TAVILY",
                            "updated_at" to 1700000000000L,
                        ),
                )
            val record =
                SearchToolSettingsRecord(
                    searchEnabled = true,
                    xSearchEnabled = false,
                    searchProvider = SearchProvider.TAVILY,
                    updatedAt = 1700000000000L,
                )

            handler.upsertLocal(record, doc, overwrite = true)

            assertTrue(
                repository.settingsFlow.first().searchEnabled,
                "searchEnabled must remain true even when the local key is gone — " +
                    "the toggle and the key are independent",
            )
        }

    @Test
    fun `S-MUTATION-003 provider-only change is propagated`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val queue = createQueue(backgroundScope)
            val repository =
                createSettingsRepository(
                    dataStore = dataStore,
                    initialProvider = SearchProvider.TAVILY,
                )
            val ctx = createContext(queue = queue, settingsRepository = repository)
            val handler = SearchToolSettingsSyncHandler(ctx)
            val doc =
                mockSearchToolSettingsDocument(
                    entityId = "main",
                    data =
                        mapOf(
                            "search_enabled" to false,
                            "x_search_enabled" to false,
                            "search_provider" to "EXA",
                            "updated_at" to 1700000000000L,
                        ),
                )
            val record =
                SearchToolSettingsRecord(
                    searchEnabled = false,
                    xSearchEnabled = false,
                    searchProvider = SearchProvider.EXA,
                    updatedAt = 1700000000000L,
                )

            handler.upsertLocal(record, doc, overwrite = true)

            val settings = repository.settingsFlow.first()
            assertEquals(
                SearchProvider.EXA,
                settings.searchProvider,
                "searchProvider must be EXA after remote apply",
            )
            assertFalse(
                settings.searchEnabled,
                "searchEnabled must be false after remote apply (the booleans must be written, not skipped)",
            )
            assertFalse(
                settings.xSearchEnabled,
                "xSearchEnabled must be false after remote apply (the booleans must be written, not skipped)",
            )
        }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun createQueue(scope: CoroutineScope): SearchToolSettingsSyncQueue =
        SearchToolSettingsSyncQueue(dataStore = createDataStore(scope))

    private fun createDataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = {
                tempDir
                    .resolve("search-settings-${System.nanoTime()}.preferences_pb")
                    .toFile()
            },
        )

    private suspend fun createSettingsRepository(
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        initialProvider: SearchProvider = SearchProvider.TAVILY,
        initialSearchEnabled: Boolean = false,
        initialXSearchEnabled: Boolean = false,
        initialTavilyKeyPresent: Boolean = false,
    ): SettingsRepositoryImpl {
        // Real ApiKeyManager over in-memory prefs: `has` mirrors the seeded keys,
        // matching the stubbed alias surface (TAVILY seeded only when requested).
        val apiKeyManager = testApiKeyManager()
        if (initialTavilyKeyPresent) {
            apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "test-tavily-key")
        }
        // Seed the initial state in the DataStore so the test reads the desired starting
        // values from `settingsFlow` without having to call the public mutators (which
        // would also enqueue a pending-sync write through the queue). We must use the
        // suspend `dataStore.edit` here (not `runBlocking`) because the test runs in
        // `runTest { }` and `runBlocking` from inside the test body would deadlock the
        // virtual-time scheduler that drives the DataStore's internal actor.
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("settings_search_enabled")] = initialSearchEnabled
            preferences[booleanPreferencesKey("settings_x_search_enabled")] = initialXSearchEnabled
            preferences[stringPreferencesKey("settings_search_provider")] = initialProvider.name
            if (initialTavilyKeyPresent) {
                preferences[booleanPreferencesKey("settings_tavily_key_present")] = true
            }
        }
        return SettingsRepositoryImpl(
            dataStore = dataStore,
            apiKeyManager = apiKeyManager,
        )
    }

    private fun createContext(
        queue: SearchToolSettingsSyncQueue,
        settingsRepository: SettingsRepositoryImpl? = null,
    ): FirestoreSyncContext =
        testSyncContext(
            userId = "test-user-id",
            searchToolSettingsSyncQueue = queue,
            settingsRepository = settingsRepository,
        )

    private fun mockSearchToolSettingsDocument(
        entityId: String,
        data: Map<String, Any?>,
    ): DocumentSnapshot {
        val parent = mockk<CollectionReference>(relaxed = true)
        every { parent.id } returns "search_tool_settings"
        val reference = mockk<DocumentReference>(relaxed = true)
        every { reference.parent } returns parent
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.id } returns entityId
        every { doc.reference } returns reference
        data.forEach { (field, value) ->
            when (value) {
                is String -> every { doc.getString(field) } returns value
                is Long -> every { doc.getLong(field) } returns value
                is Boolean -> every { doc.getBoolean(field) } returns value
                null -> every { doc.getString(field) } returns null
                else -> Unit
            }
        }
        // explicit "field absent" — getString/getLong/getBoolean all return null for missing keys
        listOf("search_enabled", "x_search_enabled", "search_provider", "updated_at")
            .forEach { field ->
                if (!data.containsKey(field)) {
                    every { doc.getString(field) } returns null
                    every { doc.getLong(field) } returns null
                    every { doc.getBoolean(field) } returns null
                }
            }
        return doc
    }
}
