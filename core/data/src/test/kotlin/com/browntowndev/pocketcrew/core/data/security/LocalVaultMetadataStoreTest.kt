package com.browntowndev.pocketcrew.core.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocalVaultMetadataStoreTest {
    private val dataStore = mockk<DataStore<Preferences>>()
    private val state = MutableStateFlow<Preferences>(preferencesOf())
    private lateinit var store: LocalVaultMetadataStore

    @BeforeEach
    fun setUp() {
        coEvery { dataStore.data } returns state
        coEvery { dataStore.updateData(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val transform = it.invocation.args[0] as suspend (Preferences) -> Preferences
            val updated = transform(state.value)
            state.value = updated
            updated
        }
        store = LocalVaultMetadataStore(dataStore)
    }

    @Test
    fun `getWrappedDek returns null when unset`() =
        runTest {
            assertNull(store.getWrappedDek())
        }

    @Test
    fun `saveWrappedDek persists and round trips`() =
        runTest {
            store.saveWrappedDek("AAAA-BBBB-CCCC")

            assertEquals("AAAA-BBBB-CCCC", store.getWrappedDek())
        }

    @Test
    fun `saveWrappedDek with null clears the existing value`() =
        runTest {
            store.saveWrappedDek("blob")
            assertEquals("blob", store.getWrappedDek())

            store.saveWrappedDek(null)

            assertNull(store.getWrappedDek())
        }

    @Test
    fun `wrappedDek key is independent of salt and verification keys`() =
        runTest {
            store.saveVaultSalt("the-salt")
            store.saveVaultVerificationData("ver")
            store.saveWrappedDek("dek-blob")

            assertEquals("the-salt", store.getVaultSalt())
            assertEquals("ver", store.getVaultVerificationData())
            assertEquals("dek-blob", store.getWrappedDek())
        }
}
