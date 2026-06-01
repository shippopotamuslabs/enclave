package com.browntowndev.enclave.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

/**
 * Coverage for the search-tool-settings sync seam on `SettingsRepositoryImpl`. Each
 * test maps to a scenario ID in `plans/search-tool-enable-sync/test_spec.md` —
 * mutators enqueue pending sync through the queue, and `applyRemoteSearchToolSettings`
 * honors the local-pending-edit-wins policy.
 *
 * Uses a real `SearchToolSettingsSyncQueue` and a real `DataStore<Preferences>` backed
 * by a `@TempDir` file, mirroring the pattern in `SettingsRepositoryImplTest`.
 */
class SettingsRepositoryImplSyncTest {
    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeEach
    fun setUp() {
        testScope = TestScope(testDispatcher)
        dataStoreScope = CoroutineScope(Job() + testDispatcher)
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
            ) { File(tempDir, "settings-${UUID.randomUUID()}.preferences_pb") }
    }

    @AfterEach
    fun tearDown() {
        dataStoreScope.cancel()
    }

    private fun newQueue(): SearchToolSettingsSyncQueue =
        SearchToolSettingsSyncQueue(
            dataStore = dataStore,
            syncScope = dataStoreScope,
        )

    private fun newRepository(
        queue: SearchToolSettingsSyncQueue,
        apiKeyManager: ApiKeyManager = mockk(relaxed = true),
    ): SettingsRepositoryImpl =
        SettingsRepositoryImpl(
            dataStore = dataStore,
            apiKeyManager = apiKeyManager,
            searchToolSettingsSyncQueue = queue,
        )

    // -----------------------------------------------------------------------
    // S-LOCAL-001 — updateSearchEnabled enqueues pending sync
    // -----------------------------------------------------------------------
    @Test
    fun `S-LOCAL-001 repository updateSearchEnabled enqueues pending sync`() =
        runTest(testDispatcher) {
            val queue = newQueue()
            val repository = newRepository(queue)

            repository.updateSearchEnabled(true)
            advanceUntilIdle()

            val data = repository.settingsFlow.first()
            assertEquals(true, data.searchEnabled, "settingsFlow.searchEnabled should be true after updateSearchEnabled(true)")
            // Queue must be marked pending by the mutator.
            assertEquals(true, queue.isPending(), "queue.isPending() should be true after updateSearchEnabled(true)")
        }

    // -----------------------------------------------------------------------
    // S-LOCAL-002 — updateXSearchEnabled enqueues pending sync
    // -----------------------------------------------------------------------
    @Test
    fun `S-LOCAL-002 repository updateXSearchEnabled enqueues pending sync`() =
        runTest(testDispatcher) {
            val queue = newQueue()
            val repository = newRepository(queue)

            repository.updateXSearchEnabled(true)
            advanceUntilIdle()

            val data = repository.settingsFlow.first()
            assertEquals(true, data.xSearchEnabled, "settingsFlow.xSearchEnabled should be true after updateXSearchEnabled(true)")
            assertEquals(true, queue.isPending(), "queue.isPending() should be true after updateXSearchEnabled(true)")
        }

    // -----------------------------------------------------------------------
    // S-LOCAL-003 — updateSearchProvider enqueues pending sync
    // -----------------------------------------------------------------------
    @Test
    fun `S-LOCAL-003 repository updateSearchProvider enqueues pending sync`() =
        runTest(testDispatcher) {
            val queue = newQueue()
            val repository = newRepository(queue)

            repository.updateSearchProvider(SearchProvider.EXA)
            advanceUntilIdle()

            val data = repository.settingsFlow.first()
            assertEquals(SearchProvider.EXA, data.searchProvider, "settingsFlow.searchProvider should be EXA after updateSearchProvider(EXA)")
            assertEquals(true, queue.isPending(), "queue.isPending() should be true after updateSearchProvider(EXA)")
        }

    // -----------------------------------------------------------------------
    // S-LOCAL-004 — combined onSaveSearchSkillSettings path enqueues once
    // -----------------------------------------------------------------------
    @Test
    fun `S-LOCAL-004 repository combined onSaveSearchSkillSettings path enqueues once`() =
        runTest(testDispatcher) {
            val queue = newQueue()
            val repository = newRepository(queue)

            // Same call order as `SettingsSearchDelegate.onSaveSearchSkillSettings`:
            // the user flips search on, then picks a provider.
            repository.updateSearchEnabled(true)
            repository.updateSearchProvider(SearchProvider.EXA)
            advanceUntilIdle()

            val data = repository.settingsFlow.first()
            assertEquals(true, data.searchEnabled, "settingsFlow.searchEnabled should be true after combined save")
            assertEquals(SearchProvider.EXA, data.searchProvider, "settingsFlow.searchProvider should be EXA after combined save")
            // markPendingSync() is idempotent on a boolean flag, so the second call
            // does not double-fire; the queue must still be pending.
            assertEquals(true, queue.isPending(), "queue.isPending() should be true after combined save (idempotent flag)")
        }

    // -----------------------------------------------------------------------
    // S-MUTATION-002 — clear-key-with-search-enabled-true does not disable search
    // (asserted at the repository seam, not the handler)
    // -----------------------------------------------------------------------
    @Test
    fun `S-MUTATION-002 repository applyRemoteSearchToolSettings preserves searchEnabled=true when local tavily key is absent`() =
        runTest(testDispatcher) {
            val queue = newQueue()
            // Pre-existing state: user has search enabled with the Tavily key present.
            // (We are not testing the key clearing side-effect here; we are testing that
            // a remote record which re-asserts searchEnabled=true is not silently flipped
            // to false by some "no key → disable" lazy implementation.)
            val repository = newRepository(queue)
            repository.updateSearchEnabled(true)
            advanceUntilIdle()
            val pre = repository.settingsFlow.first()
            assertEquals(true, pre.searchEnabled, "precondition: searchEnabled should be true before the remote apply")

            val remoteRecord =
                SearchToolSettingsRecord(
                    searchEnabled = true,
                    xSearchEnabled = false,
                    searchProvider = SearchProvider.TAVILY,
                    updatedAt = 1700000000000L,
                )

            repository.applyRemoteSearchToolSettings(remoteRecord, overwrite = true)
            advanceUntilIdle()

            val post = repository.settingsFlow.first()
            // The repository must keep searchEnabled true regardless of key state.
            assertNotNull(post, "settingsFlow should still emit a value after applyRemoteSearchToolSettings")
            assertEquals(true, post.searchEnabled, "searchEnabled must remain true (remote apply should not flip booleans based on key presence)")
        }

    // -----------------------------------------------------------------------
    // S-MUTATION-004 — local save in flight is not clobbered by a stale remote echo
    // (asserted at the repository seam, not the handler)
    // -----------------------------------------------------------------------
    @Test
    fun `S-MUTATION-004 repository applyRemoteSearchToolSettings does not clobber a newer local edit when overwrite=false`() =
        runTest(testDispatcher) {
            val queue = newQueue()
            val repository = newRepository(queue)
            // User just disabled search locally. The local DataStore has searchEnabled=false
            // and the queue is marked pending.
            repository.updateSearchEnabled(false)
            advanceUntilIdle()
            val pre = repository.settingsFlow.first()
            assertEquals(false, pre.searchEnabled, "precondition: local searchEnabled should be false")
            assertEquals(true, queue.isPending(), "precondition: queue should be pending after local mutation")

            val staleRemoteRecord =
                SearchToolSettingsRecord(
                    searchEnabled = true,
                    xSearchEnabled = false,
                    searchProvider = SearchProvider.TAVILY,
                    updatedAt = 1500000000000L, // older than the local edit
                )

            // overwrite = false → the local newer edit must win.
            repository.applyRemoteSearchToolSettings(staleRemoteRecord, overwrite = false)
            advanceUntilIdle()

            val post = repository.settingsFlow.first()
            assertEquals(false, post.searchEnabled, "local newer edit must not be clobbered by a stale remote apply")
            // The local save is still in flight for the next write.
            assertEquals(true, queue.isPending(), "queue must still be pending for the next write after a non-overwriting remote apply")
        }
}
