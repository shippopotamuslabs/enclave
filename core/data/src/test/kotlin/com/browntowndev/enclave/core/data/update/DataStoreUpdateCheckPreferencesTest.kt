package com.browntowndev.enclave.core.data.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Round-trip coverage for [DataStoreUpdateCheckPreferences] against a real
 * `PreferenceDataStoreFactory` backed by a `@TempDir` file, matching the
 * `SearchToolSettingsSyncQueueTest` pattern.
 */
@DisplayName("DataStoreUpdateCheckPreferences")
class DataStoreUpdateCheckPreferencesTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `fresh store reads zero defaults`() =
        runTest {
            val prefs = createPreferences(backgroundScope)

            assertEquals(0L, prefs.lastCheckMillis())
            assertEquals(0, prefs.dismissedVersionCode())
        }

    @Test
    fun `last check millis round-trips`() =
        runTest {
            val prefs = createPreferences(backgroundScope)

            prefs.setLastCheckMillis(1_760_000_000_000L)

            assertEquals(1_760_000_000_000L, prefs.lastCheckMillis())
        }

    @Test
    fun `dismissed version code round-trips`() =
        runTest {
            val prefs = createPreferences(backgroundScope)

            prefs.setDismissedVersionCode(7)

            assertEquals(7, prefs.dismissedVersionCode())
        }

    private fun createPreferences(scope: CoroutineScope): DataStoreUpdateCheckPreferences =
        DataStoreUpdateCheckPreferences(
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = {
                        tempDir
                            .resolve("update-check-${System.nanoTime()}.preferences_pb")
                            .toFile()
                    },
                ),
        )
}
